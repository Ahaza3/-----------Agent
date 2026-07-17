/**
 * 可视化大屏 — Brutalist CRT Terminal 风格
 * Phase 2: 数据来源可视化 / 断线标记 / 趋势卡 / 预测摘要 / 图表工具栏 / 告警标记
 */
import { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { DatePicker, Segmented, Tag, message, Button, Tooltip } from 'antd'
import {
  ThunderboltOutlined, ZoomInOutlined, DownloadOutlined,
  AlertOutlined, RobotOutlined, FileTextOutlined,
} from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import type { EChartsOption } from 'echarts'
import ReactECharts from 'echarts-for-react'
import LoadChart from '../../components/LoadChart'
import { fetchLoadRange, fetchRealtimeRecent } from '../../services/dataApi'
import { fetchForecast } from '../../services/predictApi'
import { fetchAlertRules, fetchAlertEvents } from '../../services/alertApi'
import * as ticketApi from '../../services/ticketApi'
import useDashboardStore from '../../stores/useDashboardStore'
import useAuthStore from '../../stores/useAuthStore'
import type { Role } from '../../config/roles'
import { ALERT_LEVEL_CONFIG } from '../../types/alert'
import type { AlertEvent } from '../../types/alert'
import AlertTicketDetail from '../AlertCenter/shared/AlertTicketDetail'
import type { Ticket } from '../../services/ticketApi'
import { buildLoadSeriesSegments } from './loadSeries'

const { RangePicker } = DatePicker
type QuickRange = '24h' | '7d' | '30d'

const RED = '#FF2A2A'
const YELLOW = '#FAAD14'
const WHITE = '#EAEAEA'

const TREND_THRESHOLD = 0.02

interface AlertThresholds { yellow: number; orange: number; red: number }
interface GapInterval { start: number; end: number }

function quickToRange(q: QuickRange): [Dayjs, Dayjs] {
  const now = dayjs()
  switch (q) {
    case '24h': return [now.subtract(24, 'hour'), now]
    case '7d': return [now.subtract(7, 'day'), now]
    case '30d': return [now.subtract(30, 'day'), now]
  }
}

function isRangeCoveringNow(endTime: Dayjs): boolean {
  return dayjs().diff(endTime, 'minute') <= 5
}

function isHourlyHistoryPoint(time: string): boolean {
  const value = dayjs(time)
  return value.isValid() && value.minute() === 0 && value.second() === 0
}

function fmtMw(v: number | undefined | null): string {
  if (v == null) return '--'
  return v.toFixed(1)
}

function relativeTime(ts: number): string {
  const s = Math.floor((Date.now() - ts) / 1000)
  if (s < 60) return `${s}秒前`
  if (s < 3600) return `${Math.floor(s / 60)}分钟前`
  return `${Math.floor(s / 3600)}小时前`
}

const SOURCE_LABELS: Record<string, string> = {
  MOCK_HISTORY: '模拟历史',
  RECOVERED_SIMULATION: '模拟恢复',
  MOCK_REALTIME: '实时模拟',
  METER: '电表采集',
}

const Dashboard = () => {
  const [quickRange, setQuickRange] = useState<QuickRange>('24h')
  const [customRange, setCustomRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [fetchingForecast, setFetchingForecast] = useState(false)
  const [predictionError, setPredictionError] = useState('')
  const [alertThresholds, setAlertThresholds] = useState<AlertThresholds>({ yellow: 990, orange: 1100, red: 1210 })
  const [gaps, setGaps] = useState<GapInterval[]>([])
  const [showThresholds, setShowThresholds] = useState(true)
  const [showRecovery, setShowRecovery] = useState(true)
  const [alertMarks, setAlertMarks] = useState<AlertEvent[]>([])
  const [selectedAlert, setSelectedAlert] = useState<AlertEvent | null>(null)
  const [alertTicket, setAlertTicket] = useState<Ticket | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const chartRef = useRef<ReactECharts>(null)
  const connectedRef = useRef(true)

  const user = useAuthStore((s) => s.user)
  const role = user?.role
  const userId = user?.id
  const {
    loadData, liveLoad, realtimeLoads, forecast, wsConnected, lastRealtimeAt,
    setLoadData, setForecast, mergeRealtimeLoads,
  } = useDashboardStore()

  const rangeEnd = customRange ? customRange[1] : quickToRange(quickRange)[1]
  const isLive = isRangeCoveringNow(rangeEnd)
  const hourlyLoadData = useMemo(() => loadData.filter((p) => isHourlyHistoryPoint(p.time)), [loadData])

  // -- 实时快照 --
  useEffect(() => {
    fetchRealtimeRecent(30).then((pts) => { if (pts.length > 0) mergeRealtimeLoads(pts) }).catch(() => {})
  }, [mergeRealtimeLoads])

  // -- 断线缺口追踪 --
  useEffect(() => {
    const prev = connectedRef.current
    if (prev && !wsConnected) {
      setGaps((g) => [...g, { start: Date.now(), end: 0 }])
    }
    if (!prev && wsConnected) {
      setGaps((g) => {
        const updated = [...g]
        const last = updated[updated.length - 1]
        if (last && last.end === 0) {
          last.end = Date.now()
        }
        return updated
      })
    }
    connectedRef.current = wsConnected
  }, [wsConnected])

  // -- 告警标记 --
  useEffect(() => {
    const range = customRange ?? quickToRange(quickRange)
    fetchAlertEvents({
      start: range[0].toISOString(),
      end: range[1].toISOString(),
      page: 1, size: 100,
    }).then((r) => setAlertMarks(r.records || [])).catch(() => {})
  }, [quickRange, customRange])

  // -- 告警规则 --
  useEffect(() => {
    fetchAlertRules().then((rules) => {
      const active = rules.find((r) => r.isActive === 1 && r.type === 'THRESHOLD')
      if (!active) return
      try {
        const c = JSON.parse(active.config)
        if (!c.threshold) return
        setAlertThresholds({
          yellow: c.threshold * (c.yellowRatio ?? 0.9),
          orange: c.threshold * (c.orangeRatio ?? 1),
          red: c.threshold * (c.redRatio ?? 1.1),
        })
      } catch {}
    }).catch(() => {})
  }, [])

  // -- 基础数据 --
  const fetchBase = useCallback(async () => {
    const range = customRange ?? quickToRange(quickRange)
    try {
      const data = await fetchLoadRange(range[0].toISOString(), range[1].toISOString())
      setLoadData(data)
    } catch { message.error('数据加载失败') }
    finally { /* no-op */ }
  }, [quickRange, customRange, setLoadData])

  useEffect(() => { fetchBase() }, [fetchBase])

  useEffect(() => {
    const t = setInterval(() => {
      const range = customRange ?? quickToRange(quickRange)
      fetchLoadRange(range[0].toISOString(), range[1].toISOString()).then((data) => {
        const cur = useDashboardStore.getState().loadData.slice(-1)[0]
        const n = data[data.length - 1]
        if (!cur || (n && n.time !== cur.time)) useDashboardStore.getState().setLoadData(data)
      }).catch(() => {})
    }, 30_000)
    return () => clearInterval(t)
  }, [quickRange, customRange])

  // -- 预测 --
  useEffect(() => {
    setFetchingForecast(true)
    setPredictionError('')
    fetchForecast()
      .then((d) => setForecast(d))
      .catch((error: unknown) => {
        setPredictionError(error instanceof Error ? error.message : '预测数据加载失败')
      })
      .finally(() => setFetchingForecast(false))
  }, [setForecast])

  // -- 5分钟趋势计算 --
  const trend5m = useMemo(() => {
    if (realtimeLoads.length < 2) return { change: null, pct: null, direction: '--' as const }
    const latest = realtimeLoads[realtimeLoads.length - 1]
    const FIVE_MIN_MS = 5 * 60 * 1000
    const target = latest.timestamp - FIVE_MIN_MS
    let closest = realtimeLoads[0]
    for (const p of realtimeLoads) {
      if (p.timestamp <= target) closest = p
    }
    const change = latest.loadMw - closest.loadMw
    const pct = closest.loadMw > 0 ? change / closest.loadMw : 0
    const dir = pct > TREND_THRESHOLD ? '上升' : pct < -TREND_THRESHOLD ? '下降' : '平稳'
    return { change, pct, direction: dir }
  }, [realtimeLoads])

  // -- 1小时趋势计算 --
  const trend1h = useMemo(() => {
    if (!liveLoad?.loadMw || hourlyLoadData.length < 2) return { change: null, pct: null, direction: '--' as const }
    const latest = liveLoad.loadMw
    const oneHourAgo = hourlyLoadData[hourlyLoadData.length - 1]
    const change = latest - oneHourAgo.loadMw
    const pct = oneHourAgo.loadMw > 0 ? change / oneHourAgo.loadMw : 0
    const dir = pct > TREND_THRESHOLD ? '上升' : pct < -TREND_THRESHOLD ? '下降' : '平稳'
    return { change, pct, direction: dir }
  }, [liveLoad, hourlyLoadData])

  // -- 预测摘要 --
  const predSummary = useMemo(() => {
    if (!forecast?.predictions?.length) return null
    const vals = forecast.predictions
    const peak = Math.max(...vals)
    const valley = Math.min(...vals)
    const avg = vals.reduce((a, b) => a + b, 0) / vals.length
    const peakIdx = vals.indexOf(peak)
    const valleyIdx = vals.indexOf(valley)
    const start = forecast.forecastStartTime ? dayjs(forecast.forecastStartTime) : dayjs().startOf('hour')
    const peakTime = start.add(peakIdx, 'hour')
    const valleyTime = start.add(valleyIdx, 'hour')
    const crossesYellow = peak >= alertThresholds.yellow
    const crossesOrange = peak >= alertThresholds.orange
    const crossesRed = peak >= alertThresholds.red
    const riskLevel = crossesRed ? 'RED' : crossesOrange ? 'ORANGE' : crossesYellow ? 'YELLOW' : null
    return { peak, peakTime, valley, valleyTime, avg, riskLevel, model: forecast.model, crossesYellow, crossesOrange, crossesRed }
  }, [forecast, alertThresholds])

  // -- 实时负荷曲线（全量构建，不设 dataZoom start/end，ECharts 自带保留缩放）--
  const loadChartOption = useMemo<EChartsOption>(() => {
    const histPoints = hourlyLoadData.length > 500 ? hourlyLoadData.slice(-500) : hourlyLoadData
    const { historyData, recoveryData, bridgeData, realtimeData } = buildLoadSeriesSegments(
      histPoints,
      realtimeLoads,
      isLive,
    )

    const series: any[] = [
      {
        id: 'hist', name: SOURCE_LABELS.MOCK_HISTORY || '历史数据', type: 'line', data: historyData,
        smooth: true, symbol: 'none', lineStyle: { color: WHITE, width: 1.5 },
        connectNulls: false,
      },
    ]

    // 始终三条 series，保证 legend/结构稳定；通过数据控制可见性
    series.push({
      id: 'recovery', name: SOURCE_LABELS.RECOVERED_SIMULATION || '恢复模拟', type: 'line',
      data: showRecovery ? recoveryData : [],
      smooth: true, symbol: 'none', lineStyle: { color: '#555', width: 1, type: 'dashed' },
      connectNulls: false,
    })
    series.push({
      id: 'bridge', name: '', type: 'line', data: bridgeData,
      smooth: false, symbol: 'none', animation: false, silent: true,
      lineStyle: { color: '#777', width: 1, type: 'dashed' },
      connectNulls: false,
    })
    series.push({
      id: 'realtime', name: SOURCE_LABELS.MOCK_REALTIME || '实时模拟', type: 'line',
      data: realtimeData.length > 0 ? realtimeData : [],
      smooth: false, sampling: 'lttb', symbol: 'none', animation: false,
      lineStyle: { color: '#4AF626', width: 2 },
      connectNulls: false,
    })

    // markArea: 断线缺口
    const closed = gaps.filter((g) => g.end > 0)
    if (closed.length > 0) {
      series[0].markArea = {
        silent: true,
        data: closed.map((g) => [
          { xAxis: g.start, itemStyle: { color: 'rgba(255,42,42,0.08)' } },
          { xAxis: g.end },
        ]),
        label: { show: true, formatter: '连接中断', color: RED, fontSize: 10 },
      }
    }

    // markPoint: 告警标记
    const levelGroups = new Map<string, any[]>()
    for (const a of alertMarks) {
      const ts = new Date(a.triggerTime).getTime()
      const cfg = ALERT_LEVEL_CONFIG[a.level]
      if (!levelGroups.has(a.level)) levelGroups.set(a.level, [])
      levelGroups.get(a.level)!.push({
        name: `#${a.id}`, coord: [ts, a.currentValue],
        symbol: 'pin', symbolSize: 14,
        itemStyle: { color: cfg?.color || RED },
        label: { show: true, fontSize: 9, color: '#fff', formatter: `{b}` },
      })
    }
    if (levelGroups.size > 0) {
      series[0].markPoint = { data: Array.from(levelGroups.values()).flat(), symbol: 'pin', symbolSize: 14 }
    }

    // 阈值线 — 始终设置以支持 notMerge=false 下的清除
    series[0].markLine = showThresholds ? {
      silent: true, symbol: 'none', label: { position: 'insideEndTop', fontSize: 10 },
      data: [
        { name: `黄 ${alertThresholds.yellow.toFixed(0)}`, yAxis: alertThresholds.yellow, lineStyle: { color: '#FADB14', type: 'dashed', width: 1 }, label: { color: '#FADB14' } },
        { name: `橙 ${alertThresholds.orange.toFixed(0)}`, yAxis: alertThresholds.orange, lineStyle: { color: '#FA8C16', type: 'dashed', width: 1 }, label: { color: '#FA8C16' } },
        { name: `红 ${alertThresholds.red.toFixed(0)}`, yAxis: alertThresholds.red, lineStyle: { color: RED, type: 'dashed', width: 1 }, label: { color: RED } },
      ],
    } : { data: [] }

    return {
      animation: false,
      grid: { top: 28, left: 56, right: 32, bottom: 80 },
      xAxis: { type: 'time', axisLabel: { formatter: (v: number) => dayjs(v).format(quickRange === '24h' ? 'HH:mm' : 'MM-DD') } },
      yAxis: { type: 'value', name: 'MW' },
      tooltip: {
        trigger: 'axis',
        formatter: (params: any[]) => {
          if (!params?.length) return ''
          const p = params[0]
          return `<div style="font-family:monospace">${dayjs(p.axisValue).format('MM-DD HH:mm:ss')}<br/>负荷: <strong>${p.value[1]?.toFixed(1) || '--'} MW</strong><br/>来源: ${p.seriesName || '--'}</div>`
        },
      },
      legend: { data: series.map((s) => s.name).filter(Boolean), textStyle: { color: '#888', fontSize: 10 }, type: 'scroll', bottom: 0 },
      dataZoom: [
        { type: 'inside', minSpan: 5 },
        { type: 'slider', height: 18, bottom: 24, borderColor: '#2A2A2A', backgroundColor: '#0A0A0A', fillerColor: 'rgba(255,42,42,0.12)', handleStyle: { color: RED }, textStyle: { color: '#888' } },
      ],
      series,
    } as EChartsOption
  }, [hourlyLoadData, realtimeLoads, quickRange, isLive, alertThresholds, gaps, alertMarks, showThresholds, showRecovery])

  // -- 预测曲线 --
  const predChartOption = useMemo<EChartsOption>(() => {
    if (!forecast) return { series: [] }
    const PRED_CTX: Record<QuickRange, number> = { '24h': 48, '7d': 168, '30d': 336 }
    const ctxLen = PRED_CTX[quickRange]
    const recent = hourlyLoadData.length > ctxLen ? hourlyLoadData.slice(-ctxLen) : hourlyLoadData
    const startTime = forecast.forecastStartTime ? dayjs(forecast.forecastStartTime) : dayjs().startOf('hour')
    const forecastData: [string, number][] = forecast.predictions.map((v, i) => [startTime.add(i, 'hour').toISOString(), v])
    return {
      animation: false,
      grid: { top: 28, left: 56, right: 32, bottom: 80 },
      xAxis: { type: 'time', axisLabel: { formatter: (v: number) => dayjs(v).format('MM-DD HH:mm') } },
      yAxis: { type: 'value', name: 'MW' },
      legend: { data: ['实际', '预测'], textStyle: { color: '#888', fontSize: 10 }, bottom: 0 },
      dataZoom: [{ type: 'inside', minSpan: 5 }, { type: 'slider', height: 18, bottom: 24, borderColor: '#2A2A2A', backgroundColor: '#0A0A0A', fillerColor: 'rgba(250,173,20,0.12)', handleStyle: { color: YELLOW }, textStyle: { color: '#888' } }],
      series: [
        ...(recent.length > 0 ? [{ id: 'fc-actual', name: '实际', type: 'line' as const, data: recent.map((d) => [d.time, d.loadMw]), smooth: true, symbol: 'none' as const, lineStyle: { color: WHITE, width: 1 } }] : []),
        { id: 'fc-pred', name: '预测', type: 'line', data: forecastData, smooth: true, symbol: 'none', lineStyle: { color: YELLOW, width: 1.5, type: 'dashed' }, markLine: { silent: true, symbol: 'none', lineStyle: { color: YELLOW, type: 'dashed', width: 1 }, data: [{ xAxis: startTime.toISOString() }], label: { formatter: '预测起点', color: YELLOW, fontSize: 11 } } },
      ],
    }
  }, [hourlyLoadData, forecast, quickRange])

  // -- 趋势卡 --
  const trendColor = (dir: string) => dir === '上升' ? YELLOW : dir === '下降' ? '#69b1ff' : '#888'

  // -- 导出 --
  const handleExportPng = useCallback(() => {
    const instance = chartRef.current?.getEchartsInstance()
    if (!instance) return
    const url = instance.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#0a0a0a' })
    const a = document.createElement('a')
    a.href = url; a.download = `load_chart_${dayjs().format('YYYYMMDD_HHmm')}.png`; a.click()
  }, [])

  const handleExportCsv = useCallback(() => {
    const data = hourlyLoadData
    const BOM = '﻿'
    const header = '时间,负荷(MW)'
    const rows = data.map((d) => `${d.time},${d.loadMw}`)
    const blob = new Blob([BOM + header + '\n' + rows.join('\n')], { type: 'text/csv;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob); a.download = `load_data_${dayjs().format('YYYYMMDD')}.csv`; a.click()
  }, [hourlyLoadData])

  const handleResetZoom = useCallback(() => {
    const inst = chartRef.current?.getEchartsInstance()
    if (inst) inst.dispatchAction({ type: 'dataZoom', start: 0, end: 100 })
  }, [])

  const handleQuickZoom = useCallback((minutes: number) => {
    const now = Date.now()
    const start = now - minutes * 60 * 1000
    const inst = chartRef.current?.getEchartsInstance()
    if (inst) inst.dispatchAction({ type: 'dataZoom', startValue: start, endValue: now })
  }, [])

  // -- 告警点击 --
  const handleAlertClick = useCallback(async (alertId: number) => {
    const alert = alertMarks.find((a) => a.id === alertId)
    if (!alert) return
    setSelectedAlert(alert)
    try { setAlertTicket(await ticketApi.fetchTicketByAlert(alertId)) } catch { setAlertTicket(null) }
    setDrawerOpen(true)
  }, [alertMarks])

  const chartOnEvents = useMemo(() => ({
    click: (params: any) => {
      if (params.componentType === 'markPoint' && params.data?.value) {
        handleAlertClick(params.data.value)
      }
    },
  }), [handleAlertClick])

  return (
    <div className="dashboard-root">
      {/* 标题栏 */}
      <div className="dashboard-toolbar">
        <div>
          <h1 style={{ margin: 0, fontSize: 16, fontWeight: 900 }}>
            <ThunderboltOutlined style={{ color: RED, marginRight: 6 }} />
            <span style={{ color: WHITE }}>可视化大屏</span>
            <span style={{ color: '#666', margin: '0 6px', fontSize: 12 }}>//</span>
            <span className="font-mono" style={{ color: '#888', fontSize: 11 }}>负荷监测 · 预测对比</span>
          </h1>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Segmented value={quickRange} onChange={(v) => { setQuickRange(v as QuickRange); setCustomRange(null) }}
            options={[{ label: '近24小时', value: '24h' }, { label: '近7天', value: '7d' }, { label: '近30天', value: '30d' }]} />
          <RangePicker value={customRange} onChange={(d) => { if (d?.[0] && d?.[1]) setCustomRange([d[0], d[1]]) }}
            showTime={quickRange === '24h' ? { format: 'HH:mm' } : undefined}
            format={quickRange === '24h' ? 'MM-DD HH:mm' : 'YYYY-MM-DD'}
            placeholder={['开始', '结束']} allowClear={!!customRange}
            style={{ width: quickRange === '24h' ? 340 : 280 }} />
        </div>
      </div>
      <hr className="brutalist" />

      {/* 当前负荷趋势卡 */}
      <div style={{ marginBottom: 8, padding: '10px 14px', border: '1px solid #2A2A2A', background: '#0c0c0c', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
          <span style={{ color: '#888', fontSize: 11 }}>当前负荷</span>
          <span className="font-mono" style={{ fontSize: 24, fontWeight: 700, color: WHITE }}>{fmtMw(liveLoad?.loadMw)} <small style={{ fontSize: 12, color: '#888' }}>MW</small></span>
          <span className="font-mono" style={{ color: '#888', fontSize: 11 }}>{liveLoad?.time ? dayjs(liveLoad.time).format('HH:mm:ss') : '--'}</span>
        </div>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
          <Tooltip title="较5分钟前">
            <div>
              <span style={{ color: '#888', fontSize: 10 }}>5分钟</span>
              <div className="font-mono" style={{ color: trendColor(trend5m.direction), fontSize: 13, fontWeight: 600 }}>
                {trend5m.change != null ? `${trend5m.change >= 0 ? '+' : ''}${trend5m.change.toFixed(1)} MW (${(trend5m.pct! * 100).toFixed(2)}%)` : '--'}
              </div>
            </div>
          </Tooltip>
          <Tooltip title="较上一小时（取整点历史数据）">
            <div>
              <span style={{ color: '#888', fontSize: 10 }}>1小时</span>
              <div className="font-mono" style={{ color: trendColor(trend1h.direction), fontSize: 13, fontWeight: 600 }}>
                {trend1h.change != null ? `${trend1h.change >= 0 ? '+' : ''}${trend1h.change.toFixed(1)} MW (${(trend1h.pct! * 100).toFixed(2)}%)` : '--'}
              </div>
            </div>
          </Tooltip>
          <Tooltip title={`当前趋势: ${trend5m.direction}`}>
            <Tag color={trend5m.direction === '上升' ? 'gold' : trend5m.direction === '下降' ? 'blue' : 'default'}>
              {trend5m.direction}
            </Tag>
          </Tooltip>
          <span className="font-mono" style={{ color: '#555', fontSize: 10 }}>更新: {lastRealtimeAt > 0 ? relativeTime(lastRealtimeAt) : '--'}</span>
        </div>
      </div>
      <hr className="brutalist" />

      {/* 调度员快捷操作 */}
      {role === 'DISPATCHER' && (
        <>
          <div style={{ display: 'flex', gap: 8, marginBottom: 8, flexWrap: 'wrap' }}>
            <Button size="small" icon={<AlertOutlined />} onClick={() => window.location.href = '/alerts?level=RED'}>最高级别告警</Button>
            <Button size="small" icon={<RobotOutlined />} onClick={() => window.location.href = '/agent'}>询问Agent当前风险</Button>
            <Button size="small" icon={<FileTextOutlined />} onClick={() => window.location.href = '/alerts?status=RESOLVED'}>待关闭工单</Button>
          </div>
          <hr className="brutalist" />
        </>
      )}

      {/* 预测摘要 */}
      {predSummary && (
        <div style={{ marginBottom: 8, padding: '10px 14px', border: '1px solid #2A2A2A', background: '#0c0c0c', display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
          <span style={{ color: '#888', fontSize: 11 }}>24h预测</span>
          <span style={{ color: WHITE, fontSize: 13, fontWeight: 600 }}>峰值: <span className="font-mono">{predSummary.peak.toFixed(0)} MW</span> <span style={{ color: '#666', fontSize: 10 }}>({predSummary.peakTime.format('MM-DD HH:mm')})</span></span>
          <span style={{ color: '#888', fontSize: 13 }}>谷值: <span className="font-mono">{predSummary.valley.toFixed(0)} MW</span> <span style={{ color: '#666', fontSize: 10 }}>({predSummary.valleyTime.format('MM-DD HH:mm')})</span></span>
          <span style={{ color: '#888', fontSize: 13 }}>均值: <span className="font-mono">{predSummary.avg.toFixed(0)} MW</span></span>
          {predSummary.riskLevel ? (
            <Tag color={ALERT_LEVEL_CONFIG[predSummary.riskLevel]?.color || 'red'}>
              预计{predSummary.peakTime.format('HH:mm')}进入{ALERT_LEVEL_CONFIG[predSummary.riskLevel]?.label}区间，峰值约{predSummary.peak.toFixed(0)} MW
            </Tag>
          ) : (
            <Tag color="green">预计未越过告警阈值</Tag>
          )}
          <span style={{ color: '#555', fontSize: 10 }}>模型: {predSummary.model || '--'}</span>
        </div>
      )}
      <hr className="brutalist" />

      {/* 图表工具栏 */}
      <div style={{ display: 'flex', gap: 6, marginBottom: 4, flexWrap: 'wrap', alignItems: 'center' }}>
        <Button size="small" icon={<ZoomInOutlined />} onClick={handleResetZoom}>重置缩放</Button>
        <Button size="small" onClick={() => handleQuickZoom(30)}>30分钟</Button>
        <Button size="small" onClick={() => handleQuickZoom(120)}>2小时</Button>
        <Button size="small" icon={<DownloadOutlined />} onClick={handleExportPng}>PNG</Button>
        <Button size="small" icon={<DownloadOutlined />} onClick={handleExportCsv}>CSV</Button>
        <Button size="small" type={showThresholds ? 'primary' : 'default'} onClick={() => setShowThresholds(!showThresholds)}>阈值线</Button>
        <Button size="small" type={showRecovery ? 'primary' : 'default'} onClick={() => setShowRecovery(!showRecovery)}>恢复数据</Button>
      </div>

      {/* 实时负荷曲线 */}
      <div style={{ height: 420 }}>
        <ReactECharts
          ref={chartRef}
          option={loadChartOption}
          style={{ height: '100%' }}
          notMerge={false}
          lazyUpdate
          onEvents={chartOnEvents}
        />
      </div>
      <div style={{ height: 12 }} />
      <LoadChart
        title={predictionError ? '24h 预测曲线 — 加载失败' : '24h 预测曲线'}
        option={predChartOption}
        height={300}
        loading={fetchingForecast}
        emptyText={predictionError || '暂无预测数据'}
      />

      {/* 告警详情 Drawer */}
      {selectedAlert && (
        <AlertTicketDetail
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          role={role as Role | undefined}
          userId={userId}
          alert={{ id: selectedAlert.id, level: selectedAlert.level, currentValue: selectedAlert.currentValue, thresholdValue: selectedAlert.thresholdValue, triggerTime: selectedAlert.triggerTime, aiAnalysis: selectedAlert.aiAnalysis, suggestion: selectedAlert.suggestion }}
          ticket={alertTicket}
          onTicketUpdated={setAlertTicket}
          onAssign={() => {}}
          onResolve={() => {}}
        />
      )}

      <style>{`
        .dashboard-root { min-height: calc(100vh - 132px); display: flex; flex-direction: column; }
        .dashboard-toolbar { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px; flex-wrap: wrap; gap: 12px; }
        @media (min-width: 1920px) { .dashboard-root { max-width: 1920px; margin: 0 auto; } }
        @media (max-width: 1200px) { .dashboard-toolbar { flex-direction: column; } }
        @media (max-width: 768px) {
          .dashboard-root { padding: 0; }
        }
      `}</style>
    </div>
  )
}

export default Dashboard
