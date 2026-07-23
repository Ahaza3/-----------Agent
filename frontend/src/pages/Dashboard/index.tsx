/**
 * 可视化大屏：调度员实时运行监控页。
 */
import { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { DatePicker, Segmented, Tag, message, Button, Modal, Input } from 'antd'
import {
  ThunderboltOutlined, ZoomInOutlined, DownloadOutlined,
  AlertOutlined, RobotOutlined, FileTextOutlined, BellOutlined,
} from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import type { EChartsOption } from 'echarts'
import ReactECharts from 'echarts-for-react'
import LoadChart from '../../components/LoadChart'
import { fetchLoadRange, fetchRealtimeRecent } from '../../services/dataApi'
import { fetchForecast } from '../../services/predictApi'
import { fetchAlertRules, fetchAlertEvents } from '../../services/alertApi'
import { fetchGridRisk } from '../../services/topologyApi'
import * as ticketApi from '../../services/ticketApi'
import { createPrewarningTicket } from '../../services/ticketApi'
import useDashboardStore from '../../stores/useDashboardStore'
import useAuthStore from '../../stores/useAuthStore'
import type { Role } from '../../config/roles'
import { ALERT_LEVEL_CONFIG } from '../../types/alert'
import type { AlertEvent } from '../../types/alert'
import type { GridRiskSnapshot } from '../../types/topology'
import AlertTicketDetail from '../AlertCenter/shared/AlertTicketDetail'
import type { Ticket } from '../../services/ticketApi'
import { buildLoadSeriesSegments } from './loadSeries'
import {
  enableBrowserNotifications,
  getBrowserNotificationState,
  type BrowserNotificationState,
} from '../../utils/browserNotification'

const { RangePicker } = DatePicker
type QuickRange = '24h' | '7d' | '30d'

const RED = '#D85C5C'
const YELLOW = '#D7A447'
const WHITE = '#DDEAF5'
const GREEN = '#5FA777'
const BLUE = '#7FA7C7'

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
  MOCK_HISTORY: '历史负荷',
  RECOVERED_SIMULATION: '恢复段',
  MOCK_REALTIME: '实时负荷',
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
  const [topologyRisk, setTopologyRisk] = useState<GridRiskSnapshot[]>([])
  const [selectedAlert, setSelectedAlert] = useState<AlertEvent | null>(null)
  const [alertTicket, setAlertTicket] = useState<Ticket | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [prewarningOpen, setPrewarningOpen] = useState(false)
  const [prewarningSummary, setPrewarningSummary] = useState('')
  const [creatingPrewarning, setCreatingPrewarning] = useState(false)
  const [notificationState, setNotificationState] = useState<BrowserNotificationState>(getBrowserNotificationState)
  const [freshnessClock, setFreshnessClock] = useState(Date.now())
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
  const latestRealtime = realtimeLoads[realtimeLoads.length - 1]
  const latestReceivedAt = latestRealtime?.receivedAt
    ? new Date(latestRealtime.receivedAt).getTime()
    : latestRealtime?.timestamp
  const realtimeFreshness = latestRealtime?.freshnessStatus === 'STALE'
    || (latestReceivedAt != null && freshnessClock - latestReceivedAt > 15_000) ? 'STALE' : 'FRESH'
  const realtimeQuality = latestRealtime?.qualityCode ?? 'LEGACY_UNKNOWN'
  useEffect(() => {
    const timer = window.setInterval(() => setFreshnessClock(Date.now()), 1_000)
    return () => window.clearInterval(timer)
  }, [])
  const handleEnableNotifications = useCallback(async () => {
    const state = await enableBrowserNotifications()
    setNotificationState(state)
    if (state === 'granted') message.success('浏览器告警通知已开启')
    else if (state === 'denied') message.warning('浏览器通知权限被拒绝，请在浏览器设置中重新允许')
    else if (state === 'unsupported') message.warning('当前浏览器不支持通知')
  }, [])

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

  // 拓扑风险摘要与首页负荷、告警保持同屏刷新。
  useEffect(() => {
    const refreshTopologyRisk = () => {
      fetchGridRisk().then(setTopologyRisk).catch(() => {})
    }
    refreshTopologyRisk()
    const timer = window.setInterval(refreshTopologyRisk, 15_000)
    return () => window.clearInterval(timer)
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
  const prewarningRiskLevel = predSummary?.riskLevel || 'YELLOW'

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
        smooth: true, symbol: 'none', lineStyle: { color: '#6F7D8A', width: 1, type: 'dashed' },
      connectNulls: false,
    })
    series.push({
      id: 'bridge', name: '', type: 'line', data: showRecovery || recoveryData.length === 0 ? bridgeData : [],
      smooth: true, symbol: 'none', animation: false, silent: true,
      lineStyle: { color: '#8B98A6', width: 1, type: 'dashed' },
      connectNulls: false,
    })
    series.push({
      id: 'realtime', name: SOURCE_LABELS.MOCK_REALTIME || '实时模拟', type: 'line',
      data: realtimeData.length > 0 ? realtimeData : [],
      smooth: false, sampling: 'lttb', symbol: 'none', animation: false,
      lineStyle: { color: GREEN, width: 2 },
      connectNulls: false,
    })

    // markArea: 断线缺口
    const closed = gaps.filter((g) => g.end > 0)
    if (closed.length > 0) {
      series[0].markArea = {
        silent: true,
        data: closed.map((g) => [
          { xAxis: g.start, itemStyle: { color: 'rgba(216, 92, 92, 0.1)' } },
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
        { name: `黄 ${alertThresholds.yellow.toFixed(0)}`, yAxis: alertThresholds.yellow, lineStyle: { color: '#D7A447', type: 'dashed', width: 1 }, label: { color: '#D7A447' } },
        { name: `橙 ${alertThresholds.orange.toFixed(0)}`, yAxis: alertThresholds.orange, lineStyle: { color: '#C9823D', type: 'dashed', width: 1 }, label: { color: '#C9823D' } },
        { name: `红 ${alertThresholds.red.toFixed(0)}`, yAxis: alertThresholds.red, lineStyle: { color: RED, type: 'dashed', width: 1 }, label: { color: RED } },
      ],
    } : { data: [] }

    return {
      animation: false,
      grid: { top: 28, left: 56, right: 32, bottom: 80 },
      xAxis: { type: 'time', axisLine: { lineStyle: { color: '#253341' } }, axisLabel: { color: '#7D8A97', formatter: (v: number) => dayjs(v).format(quickRange === '24h' ? 'HH:mm' : 'MM-DD') } },
      yAxis: { type: 'value', name: 'MW', splitLine: { lineStyle: { color: '#1C2935' } }, axisLabel: { color: '#7D8A97' }, nameTextStyle: { color: '#7D8A97' } },
      tooltip: {
        trigger: 'axis',
        formatter: (params: any[]) => {
          if (!params?.length) return ''
          const p = params[0]
          return `<div style="font-family:monospace">${dayjs(p.axisValue).format('MM-DD HH:mm:ss')}<br/>负荷: <strong>${p.value[1]?.toFixed(1) || '--'} MW</strong><br/>类型: ${p.seriesName || '--'}</div>`
        },
      },
      legend: { data: series.map((s) => s.name).filter(Boolean), textStyle: { color: '#7D8A97', fontSize: 11 }, type: 'scroll', bottom: 0 },
      dataZoom: [
        { type: 'inside', minSpan: 5 },
        { type: 'slider', height: 18, bottom: 24, borderColor: '#253341', backgroundColor: '#0E1620', fillerColor: 'rgba(79, 143, 186, 0.18)', handleStyle: { color: BLUE }, textStyle: { color: '#7D8A97' } },
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
    const intervalSeries = forecast.lowerBounds?.length === forecast.predictions.length
      && forecast.upperBounds?.length === forecast.predictions.length
      ? [
        {
          id: 'fc-lower', name: '参考下界', type: 'line' as const,
          data: forecast.lowerBounds.map((v, i) => [startTime.add(i, 'hour').toISOString(), v]),
          smooth: true, symbol: 'none' as const,
          lineStyle: { color: '#8B98A6', width: 1, type: 'dotted' as const },
        },
        {
          id: 'fc-upper', name: '参考上界', type: 'line' as const,
          data: forecast.upperBounds.map((v, i) => [startTime.add(i, 'hour').toISOString(), v]),
          smooth: true, symbol: 'none' as const,
          lineStyle: { color: '#8B98A6', width: 1, type: 'dotted' as const },
        },
      ]
      : []
    return {
      animation: false,
      grid: { top: 28, left: 56, right: 32, bottom: 80 },
      xAxis: { type: 'time', axisLine: { lineStyle: { color: '#253341' } }, axisLabel: { color: '#7D8A97', formatter: (v: number) => dayjs(v).format('MM-DD HH:mm') } },
      yAxis: { type: 'value', name: 'MW', splitLine: { lineStyle: { color: '#1C2935' } }, axisLabel: { color: '#7D8A97' }, nameTextStyle: { color: '#7D8A97' } },
      legend: { data: ['实际', '预测', '参考下界', '参考上界'], textStyle: { color: '#7D8A97', fontSize: 11 }, bottom: 0 },
      dataZoom: [{ type: 'inside', minSpan: 5 }, { type: 'slider', height: 18, bottom: 24, borderColor: '#253341', backgroundColor: '#0E1620', fillerColor: 'rgba(215, 164, 71, 0.16)', handleStyle: { color: YELLOW }, textStyle: { color: '#7D8A97' } }],
      series: [
        ...intervalSeries,
        ...(recent.length > 0 ? [{ id: 'fc-actual', name: '实际', type: 'line' as const, data: recent.map((d) => [d.time, d.loadMw]), smooth: true, symbol: 'none' as const, lineStyle: { color: WHITE, width: 1 } }] : []),
        { id: 'fc-pred', name: '预测', type: 'line', data: forecastData, smooth: true, symbol: 'none', lineStyle: { color: YELLOW, width: 1.5, type: 'dashed' }, markLine: { silent: true, symbol: 'none', lineStyle: { color: YELLOW, type: 'dashed', width: 1 }, data: [{ xAxis: startTime.toISOString() }], label: { formatter: '预测起点', color: YELLOW, fontSize: 11 } } },
      ],
    }
  }, [hourlyLoadData, forecast, quickRange])

  // -- 趋势卡 --
  const trendColor = (dir: string) => dir === '上升' ? YELLOW : dir === '下降' ? BLUE : '#9CAAB7'

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

  const openPrewarningTicket = useCallback(() => {
    if (!predSummary) return
    setPrewarningSummary(
      '预测' + predSummary.peakTime.format('MM-DD HH:mm')
      + '负荷可能达到 ' + predSummary.peak.toFixed(0)
      + ' MW，建议提前核查调峰余量、实时爬升趋势和运维准备。',
    )
    setPrewarningOpen(true)
  }, [predSummary])

  const submitPrewarningTicket = useCallback(async () => {
    if (!predSummary) return
    if (!prewarningSummary.trim()) {
      message.warning('请填写预警工单概要')
      return
    }
    setCreatingPrewarning(true)
    try {
      await createPrewarningTicket({
        summary: prewarningSummary.trim(),
        riskLevel: prewarningRiskLevel,
        forecastTime: predSummary.peakTime.toISOString(),
        expectedLoad: predSummary.peak,
      })
      message.success('预警工单已创建')
      setPrewarningOpen(false)
      setPrewarningSummary('')
    } catch (error: any) {
      message.error(error?.response?.data?.message || error.message || '预警工单创建失败')
    } finally {
      setCreatingPrewarning(false)
    }
  }, [predSummary, prewarningRiskLevel, prewarningSummary])

  return (
    <div className="dashboard-root">
      <div className="dashboard-toolbar">
        <div>
          <h1>
            <ThunderboltOutlined />
            运行监控
          </h1>
          <p>实时负荷、告警阈值与未来 24 小时预测</p>
        </div>
        <div className="dashboard-toolbar__filters">
          <Button
            size="small"
            icon={<BellOutlined />}
            disabled={notificationState === 'unsupported'}
            onClick={handleEnableNotifications}
          >
            {notificationState === 'granted' ? '浏览器通知已开启' : '开启浏览器通知'}
          </Button>
          <Tag color="default">演示数据</Tag>
          <Segmented value={quickRange} onChange={(v) => { setQuickRange(v as QuickRange); setCustomRange(null) }}
            options={[{ label: '近24小时', value: '24h' }, { label: '近7天', value: '7d' }, { label: '近30天', value: '30d' }]} />
          <RangePicker value={customRange} onChange={(d) => { if (d?.[0] && d?.[1]) setCustomRange([d[0], d[1]]) }}
            showTime={quickRange === '24h' ? { format: 'HH:mm' } : undefined}
            format={quickRange === '24h' ? 'MM-DD HH:mm' : 'YYYY-MM-DD'}
            placeholder={['开始', '结束']} allowClear={!!customRange}
            className="dashboard-range-picker" />
        </div>
      </div>

      <section className="dashboard-summary">
        <div className="dashboard-load-card">
          <span>当前负荷</span>
          <strong className="font-mono">{fmtMw(liveLoad?.loadMw)} <small>MW</small></strong>
          <em>{liveLoad?.time ? dayjs(liveLoad.time).format('HH:mm:ss') : '等待实时数据'}</em>
          <em>模拟数据源 · {realtimeQuality} · {realtimeFreshness === 'STALE' ? '数据陈旧' : '数据新鲜'}</em>
        </div>

        <div className="dashboard-metric-card">
          <span>5分钟变化</span>
          <strong className="font-mono" style={{ color: trendColor(trend5m.direction) }}>
            {trend5m.change != null ? `${trend5m.change >= 0 ? '+' : ''}${trend5m.change.toFixed(1)} MW` : '--'}
          </strong>
          <em>{trend5m.pct != null ? `${(trend5m.pct * 100).toFixed(2)}% · ${trend5m.direction}` : '样本不足'}</em>
        </div>

        <div className="dashboard-metric-card">
          <span>1小时变化</span>
          <strong className="font-mono" style={{ color: trendColor(trend1h.direction) }}>
            {trend1h.change != null ? `${trend1h.change >= 0 ? '+' : ''}${trend1h.change.toFixed(1)} MW` : '--'}
          </strong>
          <em>{trend1h.pct != null ? `${(trend1h.pct * 100).toFixed(2)}% · ${trend1h.direction}` : '样本不足'}</em>
        </div>

        <div className="dashboard-metric-card">
          <span>数据延迟</span>
          <strong className="font-mono">{lastRealtimeAt > 0 ? relativeTime(lastRealtimeAt) : '--'}</strong>
          <em>{realtimeFreshness === 'STALE' ? '最后有效观测数据已陈旧' : (wsConnected ? '模拟实时链路正常' : '模拟实时链路中断')}</em>
        </div>
      </section>

      {role === 'DISPATCHER' && (
        <section className="dashboard-action-row" aria-label="调度快捷操作">
          <Button size="small" icon={<AlertOutlined />} onClick={() => window.location.href = '/alerts?level=RED'}>严重告警</Button>
          <Button size="small" icon={<RobotOutlined />} onClick={() => window.location.href = '/agent'}>询问风险</Button>
          <Button size="small" icon={<FileTextOutlined />} onClick={() => window.location.href = '/alerts?status=RESOLVED'}>待关闭工单</Button>
        </section>
      )}

      {topologyRisk.length > 0 && (
        <section className="dashboard-topology-strip">
          <div className="dashboard-topology-strip__header">
            <div>
              <div className="dashboard-topology-strip__kicker">TOPOLOGY RISK</div>
              <h2>拓扑风险摘要</h2>
              <p>将节点风险、根告警和责任变电站带回运行监控首页。</p>
            </div>
            <Button size="small" onClick={() => { window.location.href = '/topology' }}>
              查看拓扑
            </Button>
          </div>
          <div className="dashboard-topology-strip__metrics">
            <span>关注节点 <b>{topologyRisk.filter((item) => item.riskLevel !== 'NORMAL').length}</b></span>
            <span>根告警 <b>{topologyRisk.filter((item) => item.alertRootNodeCode === item.nodeCode).length}</b></span>
            <span>红色风险 <b>{topologyRisk.filter((item) => item.riskLevel === 'RED').length}</b></span>
          </div>
          <div className="dashboard-topology-strip__nodes">
            {topologyRisk
              .filter((item) => item.alertRootNodeCode === item.nodeCode)
              .slice(0, 3)
              .map((item) => (
                <div key={item.nodeId} className={`dashboard-topology-node dashboard-topology-node--${item.riskLevel.toLowerCase()}`}>
                  <strong>{item.nodeName}</strong>
                  <span>{item.riskLevel} · {fmtMw(item.currentLoadMw)} MW</span>
                </div>
              ))}
          </div>
        </section>
      )}

      {predSummary && (
        <section className="dashboard-forecast-summary">
          <span>24小时预测</span>
          <strong>峰值 <b className="font-mono">{predSummary.peak.toFixed(0)} MW</b> <em>{predSummary.peakTime.format('MM-DD HH:mm')}</em></strong>
          <strong>谷值 <b className="font-mono">{predSummary.valley.toFixed(0)} MW</b> <em>{predSummary.valleyTime.format('MM-DD HH:mm')}</em></strong>
          <strong>均值 <b className="font-mono">{predSummary.avg.toFixed(0)} MW</b></strong>
          {predSummary.riskLevel ? (
            <Tag color={ALERT_LEVEL_CONFIG[predSummary.riskLevel]?.color || 'red'}>
              预计{predSummary.peakTime.format('HH:mm')}进入{ALERT_LEVEL_CONFIG[predSummary.riskLevel]?.label}区间，峰值约{predSummary.peak.toFixed(0)} MW
            </Tag>
          ) : (
            <Tag color="green">未越过告警阈值</Tag>
          )}
          {role === 'DISPATCHER' && (
            <Button size="small" type="primary" onClick={openPrewarningTicket}>
              创建预警工单
            </Button>
          )}
        </section>
      )}

      <section className="dashboard-panel">
        <div className="dashboard-panel__header">
          <div>
            <h2>实时负荷曲线</h2>
            <p>历史整点、恢复段和实时秒级数据分段展示</p>
          </div>
          <div className="dashboard-chart-tools">
            <Button size="small" icon={<ZoomInOutlined />} onClick={handleResetZoom}>重置</Button>
            <Button size="small" onClick={() => handleQuickZoom(30)}>30分钟</Button>
            <Button size="small" onClick={() => handleQuickZoom(120)}>2小时</Button>
            <Button size="small" icon={<DownloadOutlined />} onClick={handleExportPng}>PNG</Button>
            <Button size="small" icon={<DownloadOutlined />} onClick={handleExportCsv}>CSV</Button>
            <Button size="small" type={showThresholds ? 'primary' : 'default'} onClick={() => setShowThresholds(!showThresholds)}>阈值线</Button>
            <Button size="small" type={showRecovery ? 'primary' : 'default'} onClick={() => setShowRecovery(!showRecovery)}>恢复段</Button>
          </div>
        </div>
        <div className="dashboard-chart">
          <ReactECharts
            ref={chartRef}
            option={loadChartOption}
            style={{ height: '100%' }}
            notMerge={false}
            lazyUpdate
            onEvents={chartOnEvents}
          />
        </div>
      </section>

      <section className="dashboard-panel dashboard-panel--forecast">
        <LoadChart
          title={predictionError ? '24h 预测曲线 · 加载失败' : '24h 预测曲线'}
          option={predChartOption}
          height={300}
          loading={fetchingForecast}
          emptyText={predictionError || '暂无预测数据'}
        />
      </section>

      {/* 告警详情 Drawer */}
      {selectedAlert && (
        <AlertTicketDetail
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          role={role as Role | undefined}
          userId={userId}
          alert={{ id: selectedAlert.id, type: selectedAlert.type, level: selectedAlert.level, currentValue: selectedAlert.currentValue, thresholdValue: selectedAlert.thresholdValue, triggerTime: selectedAlert.triggerTime, aiAnalysis: selectedAlert.aiAnalysis, suggestion: selectedAlert.suggestion }}
          ticket={alertTicket}
          onTicketUpdated={setAlertTicket}
          onAssign={() => {}}
          onResolve={() => {}}
        />
      )}

      <Modal
        title="创建预警工单"
        open={prewarningOpen}
        onCancel={() => setPrewarningOpen(false)}
        onOk={submitPrewarningTicket}
        okText="创建"
        confirmLoading={creatingPrewarning}
      >
        <p style={{ color: '#7D8A97', fontSize: 12, marginTop: 0 }}>
          该工单基于未来预测风险创建，不代表告警已经触发。
        </p>
        <Input.TextArea
          rows={4}
          value={prewarningSummary}
          onChange={(event) => setPrewarningSummary(event.target.value)}
          maxLength={500}
          showCount
          placeholder="填写预警工单概要"
        />
      </Modal>

      <style>{`
        .dashboard-root {
          min-height: calc(100vh - 92px);
          display: flex;
          flex-direction: column;
          gap: 14px;
        }
        .dashboard-toolbar {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          flex-wrap: wrap;
          gap: 14px;
        }
        .dashboard-toolbar h1 {
          display: flex;
          align-items: center;
          gap: 8px;
          margin: 0;
          color: #E7EDF3;
          font-size: 22px;
          font-weight: 750;
          letter-spacing: 0;
        }
        .dashboard-toolbar h1 .anticon {
          color: #7FA7C7;
        }
        .dashboard-toolbar p {
          margin: 6px 0 0;
          color: #7D8A97;
          font-size: 12px;
        }
        .dashboard-toolbar__filters {
          display: flex;
          align-items: center;
          justify-content: flex-end;
          gap: 10px;
          flex-wrap: wrap;
        }
        .dashboard-range-picker {
          width: 320px;
          max-width: 100%;
        }
        .dashboard-summary {
          display: grid;
          grid-template-columns: minmax(220px, 1.4fr) repeat(3, minmax(160px, 1fr));
          gap: 12px;
        }
        .dashboard-load-card,
        .dashboard-metric-card,
        .dashboard-panel,
        .dashboard-forecast-summary {
          border: 1px solid #1C2935;
          background: linear-gradient(180deg, rgba(17, 26, 35, 0.96), rgba(14, 22, 31, 0.96));
          box-shadow: 0 12px 28px rgba(0, 0, 0, 0.16);
        }
        .dashboard-load-card,
        .dashboard-metric-card {
          min-height: 96px;
          padding: 16px;
          border-radius: 8px;
        }
        .dashboard-load-card span,
        .dashboard-metric-card span,
        .dashboard-forecast-summary > span {
          display: block;
          color: #7D8A97;
          font-size: 12px;
        }
        .dashboard-load-card strong {
          display: block;
          margin-top: 8px;
          color: #E7EDF3;
          font-size: 34px;
          font-weight: 720;
          line-height: 1;
        }
        .dashboard-load-card small {
          color: #7D8A97;
          font-size: 13px;
        }
        .dashboard-load-card em,
        .dashboard-metric-card em,
        .dashboard-forecast-summary em {
          display: block;
          margin-top: 8px;
          color: #6F7D8A;
          font-style: normal;
          font-size: 12px;
        }
        .dashboard-metric-card strong {
          display: block;
          margin-top: 10px;
          color: #E7EDF3;
          font-size: 22px;
          font-weight: 700;
        }
        .dashboard-action-row {
          display: flex;
          gap: 8px;
          flex-wrap: wrap;
        }
        .dashboard-topology-strip {
          border: 1px solid #1C2935;
          background: linear-gradient(180deg, rgba(17, 26, 35, 0.96), rgba(14, 22, 31, 0.96));
          border-radius: 8px;
          padding: 14px 16px;
        }
        .dashboard-topology-strip__header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          gap: 12px;
        }
        .dashboard-topology-strip__kicker {
          color: #7FA7C7;
          font-size: 10px;
          letter-spacing: 0.08em;
        }
        .dashboard-topology-strip h2 {
          margin: 4px 0 0;
          color: #E7EDF3;
          font-size: 15px;
        }
        .dashboard-topology-strip p {
          margin: 5px 0 0;
          color: #6F7D8A;
          font-size: 12px;
        }
        .dashboard-topology-strip__metrics {
          display: flex;
          gap: 18px;
          margin-top: 14px;
          color: #7D8A97;
          font-size: 12px;
        }
        .dashboard-topology-strip__metrics b {
          margin-left: 5px;
          color: #E7EDF3;
          font-size: 16px;
        }
        .dashboard-topology-strip__nodes {
          display: grid;
          grid-template-columns: repeat(3, minmax(0, 1fr));
          gap: 8px;
          margin-top: 12px;
        }
        .dashboard-topology-node {
          min-width: 0;
          padding: 9px 10px;
          border-left: 3px solid #5FA777;
          background: #101A23;
        }
        .dashboard-topology-node strong,
        .dashboard-topology-node span {
          display: block;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        .dashboard-topology-node strong {
          color: #DDEAF5;
          font-size: 12px;
        }
        .dashboard-topology-node span {
          margin-top: 4px;
          color: #7D8A97;
          font-size: 11px;
        }
        .dashboard-topology-node--red { border-left-color: #D85C5C; }
        .dashboard-topology-node--orange { border-left-color: #D7A447; }
        .dashboard-topology-node--yellow { border-left-color: #C6A34A; }
        .dashboard-forecast-summary {
          display: flex;
          align-items: center;
          gap: 16px;
          flex-wrap: wrap;
          padding: 12px 16px;
          border-radius: 8px;
        }
        .dashboard-forecast-summary strong {
          color: #C4CED8;
          font-size: 13px;
          font-weight: 600;
        }
        .dashboard-forecast-summary b {
          color: #E7EDF3;
          font-weight: 700;
        }
        .dashboard-forecast-summary em {
          display: inline;
          margin-left: 6px;
        }
        .dashboard-panel {
          border-radius: 8px;
          overflow: hidden;
        }
        .dashboard-panel__header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          gap: 12px;
          flex-wrap: wrap;
          padding: 14px 16px 10px;
          border-bottom: 1px solid #1C2935;
        }
        .dashboard-panel__header h2 {
          margin: 0;
          color: #E7EDF3;
          font-size: 15px;
          font-weight: 700;
        }
        .dashboard-panel__header p {
          margin: 5px 0 0;
          color: #6F7D8A;
          font-size: 12px;
        }
        .dashboard-chart-tools {
          display: flex;
          align-items: center;
          justify-content: flex-end;
          gap: 6px;
          flex-wrap: wrap;
        }
        .dashboard-chart {
          height: 420px;
          padding: 8px 8px 0;
        }
        .dashboard-panel--forecast {
          padding: 0;
        }
        @media (min-width: 1920px) {
          .dashboard-root { max-width: 1920px; margin: 0 auto; }
        }
        @media (max-width: 1280px) {
          .dashboard-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
        }
        @media (max-width: 860px) {
          .dashboard-summary { grid-template-columns: 1fr; }
          .dashboard-toolbar__filters { justify-content: flex-start; width: 100%; }
          .dashboard-range-picker { width: 100%; }
        }
        @media (max-width: 768px) {
          .dashboard-root { padding: 0; gap: 12px; }
          .dashboard-toolbar h1 { font-size: 20px; }
          .dashboard-load-card strong { font-size: 30px; }
          .dashboard-panel__header { padding: 12px; }
          .dashboard-chart { height: 360px; padding-inline: 0; }
        }
      `}</style>
    </div>
  )
}

export default Dashboard
