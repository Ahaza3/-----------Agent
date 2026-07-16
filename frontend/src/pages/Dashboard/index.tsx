/**
 * 可视化大屏 — Brutalist CRT Terminal 风格
 * P0 · Sprint 1
 *
 * 默认展示实时负荷曲线，点击"预测"后加载预测对比图
 */
import { useState, useEffect, useCallback, useMemo } from 'react'
import { DatePicker, Row, Col, Segmented, Tag, message } from 'antd'
import { ThunderboltOutlined, WifiOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import type { EChartsOption } from 'echarts'
import StatCard from '../../components/StatCard'
import LoadChart from '../../components/LoadChart'
import { fetchLoadRange, fetchLoadStats, fetchRealtimeRecent } from '../../services/dataApi'
import { fetchForecast } from '../../services/predictApi'
import { fetchAlertRules } from '../../services/alertApi'
import useDashboardStore from '../../stores/useDashboardStore'
import { calculateDisplayStats } from './dashboardStats'
import type { RealtimeLoadPoint } from '../../types/load'


const { RangePicker } = DatePicker
type QuickRange = '24h' | '7d' | '30d'

function quickToRange(q: QuickRange): [Dayjs, Dayjs] {
  const now = dayjs()
  switch (q) {
    case '24h':
      return [now.subtract(24, 'hour'), now]
    case '7d':
      return [now.subtract(7, 'day'), now]
    case '30d':
      return [now.subtract(30, 'day'), now]
  }
}

/** 判断选择的区间是否是"现在"（结束时间距离当前 ≤ 5 分钟） */
function isRangeCoveringNow(endTime: Dayjs): boolean {
  return dayjs().diff(endTime, 'minute') <= 5
}

/** load_data 只承载小时级历史数据，忽略旧版本遗留的秒级记录。 */
function isHourlyHistoryPoint(time: string): boolean {
  const value = dayjs(time)
  return value.isValid() && value.minute() === 0 && value.second() === 0
}

function fmtMw(v: number | undefined | null): string {
  if (v == null) return '--'
  return v.toFixed(1)
}

const RED = '#FF2A2A'
const YELLOW = '#E6C300'
const GREEN = '#4AF626'
const WHITE = '#EAEAEA'

interface AlertThresholds {
  yellow: number
  orange: number
  red: number
}

const Dashboard = () => {
  const [quickRange, setQuickRange] = useState<QuickRange>('24h')
  const [customRange, setCustomRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [fetching, setFetching] = useState(false)
  const [fetchingForecast, setFetchingForecast] = useState(false)
  const [alertThresholds, setAlertThresholds] = useState<AlertThresholds>({
    yellow: 990,
    orange: 1100,
    red: 1210,
  })
  const [statsRealtimeSnapshot, setStatsRealtimeSnapshot] = useState<RealtimeLoadPoint[]>([])

  const {
    loadData,
    liveLoad,
    realtimeLoads,
    forecast,
    wsConnected,
    lastRealtimeAt,
    setLoadData,
    setStats,
    setForecast,
    mergeRealtimeLoads,
  } = useDashboardStore()

  // 当前是否在查看"实时"区间
  const rangeEnd = customRange ? customRange[1] : quickToRange(quickRange)[1]
  const isLive = isRangeCoveringNow(rangeEnd)
  const hourlyLoadData = useMemo(
    () => loadData.filter((point) => isHourlyHistoryPoint(point.time)),
    [loadData],
  )

  // ---- 首次加载时拉取实时快照 ----
  useEffect(() => {
    fetchRealtimeRecent(30)
      .then((pts) => { if (pts.length > 0) mergeRealtimeLoads(pts) })
      .catch(() => { /* 后端可能未启动 */ })
  }, [mergeRealtimeLoads])

  // 每 5 秒从 store 获取 realtimeLoads 快照，用于统计卡片计算
  useEffect(() => {
    const update = () => {
      setStatsRealtimeSnapshot(useDashboardStore.getState().realtimeLoads)
    }
    update()
    const timer = window.setInterval(update, 5000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    fetchAlertRules().then((rules) => {
      const activeRule = rules.find((rule) => rule.isActive === 1 && rule.type === 'THRESHOLD')
      if (!activeRule) return
      try {
        const config = JSON.parse(activeRule.config) as {
          threshold?: number
          yellowRatio?: number
          orangeRatio?: number
          redRatio?: number
        }
        if (!config.threshold) return
        setAlertThresholds({
          yellow: config.threshold * (config.yellowRatio ?? 0.9),
          orange: config.threshold * (config.orangeRatio ?? 1),
          red: config.threshold * (config.redRatio ?? 1.1),
        })
      } catch (error) {
        console.error('[Dashboard] 告警规则解析失败', error)
      }
    }).catch(() => {})
  }, [])

  // ---- 基础数据 ----
  const fetchBase = useCallback(async () => {
    const range = customRange ?? quickToRange(quickRange)
    setFetching(true)
    try {
      const [data, st] = await Promise.all([
        fetchLoadRange(range[0].toISOString(), range[1].toISOString()),
        fetchLoadStats(range[0].toISOString(), range[1].toISOString()),
      ])
      setLoadData(data)
      setStats(st)
    } catch {
      message.error('数据加载失败 // 后端无响应')
    } finally {
      setFetching(false)
    }
  }, [quickRange, customRange, setLoadData, setStats])

  useEffect(() => {
    fetchBase()
  }, [fetchBase])

  // 每 30 秒静默刷新
  useEffect(() => {
    const timer = setInterval(() => {
      const range = customRange ?? quickToRange(quickRange)
      Promise.all([
        fetchLoadRange(range[0].toISOString(), range[1].toISOString()),
        fetchLoadStats(range[0].toISOString(), range[1].toISOString()),
      ]).then(([data, st]) => {
        const currentLast = useDashboardStore.getState().loadData.slice(-1)[0]
        const newLast = data[data.length - 1]
        if (!currentLast || (newLast && newLast.time !== currentLast.time)) {
          useDashboardStore.getState().setLoadData(data)
          setStats(st)
        }
      }).catch(() => {})
    }, 30_000)
    return () => clearInterval(timer)
  }, [quickRange, customRange, setStats])

  // ---- 首次加载时拉预测 ----
  useEffect(() => {
    setFetchingForecast(true)
    fetchForecast().then(setForecast).catch(() => {}).finally(() => setFetchingForecast(false))
  }, [setForecast])

  // ---- 实时负荷曲线 ----
  const loadChartOption = useMemo<EChartsOption>(() => {
    // 小时级历史点（控制数量）
    const histPoints = hourlyLoadData.length > 500
      ? hourlyLoadData.slice(-500)
      : hourlyLoadData
    const chartData: [string, number][] = histPoints.map((d) => [d.time, d.loadMw])

    // 仅在"实时"模式下拼接实时点
    if (isLive && realtimeLoads.length > 0) {
      // 找到历史数据最后的时间戳，只追加在这之后的实时点
      const lastHistTime = histPoints.length > 0
        ? new Date(histPoints[histPoints.length - 1].time).getTime()
        : 0

      for (const p of realtimeLoads) {
        if (p.timestamp > lastHistTime) {
          chartData.push([new Date(p.timestamp).toISOString(), p.loadMw])
        }
      }
    }

    return {
      animation: false,
      grid: { top: 28, left: 56, right: 32, bottom: 52 },
      xAxis: {
        type: 'time',
        axisLabel: {
          formatter: (v: number) =>
            dayjs(v).format(quickRange === '24h' ? 'HH:mm' : 'MM-DD'),
        },
      },
      yAxis: { type: 'value', name: 'MW' },
      dataZoom: [
        { type: 'inside', minSpan: 5 },
        {
          type: 'slider', height: 24, bottom: 6,
          borderColor: '#2A2A2A', backgroundColor: '#0A0A0A',
          fillerColor: 'rgba(255,42,42,0.12)',
          handleStyle: { color: RED }, textStyle: { color: '#888888' },
        },
      ],
      series: [
        {
          id: 'realtime-load',
          name: '负荷',
          type: 'line',
          data: chartData,
          smooth: true, symbol: 'none',
          lineStyle: { color: WHITE, width: 1.5 },
          areaStyle: { color: 'rgba(255,42,42,0.06)' },
          markLine: {
            silent: true,
            symbol: 'none',
            label: { position: 'insideEndTop', fontSize: 10 },
            data: [
              {
                name: `黄色 ${alertThresholds.yellow.toFixed(0)} MW`,
                yAxis: alertThresholds.yellow,
                lineStyle: { color: '#FADB14', type: 'dashed', width: 1 },
                label: { color: '#FADB14' },
              },
              {
                name: `橙色 ${alertThresholds.orange.toFixed(0)} MW`,
                yAxis: alertThresholds.orange,
                lineStyle: { color: '#FA8C16', type: 'dashed', width: 1 },
                label: { color: '#FA8C16' },
              },
              {
                name: `红色 ${alertThresholds.red.toFixed(0)} MW`,
                yAxis: alertThresholds.red,
                lineStyle: { color: RED, type: 'dashed', width: 1 },
                label: { color: RED },
              },
            ],
          },
        },
      ],
    }
  }, [hourlyLoadData, realtimeLoads, quickRange, isLive, alertThresholds])

  // ---- 预测曲线 ----
  const predChartOption = useMemo<EChartsOption>(() => {
    if (!forecast) {
      return { series: [] }
    }

    const PRED_CTX: Record<QuickRange, number> = { '24h': 48, '7d': 168, '30d': 336 }
    const ctxLen = PRED_CTX[quickRange]
    const recent = hourlyLoadData.length > ctxLen
      ? hourlyLoadData.slice(-ctxLen)
      : hourlyLoadData

    // 预测基准时间：优先使用后端返回的 forecastStartTime
    const startTime = forecast.forecastStartTime
      ? dayjs(forecast.forecastStartTime)
      : dayjs().startOf('hour')

    // forecastStartTime 对应 predictions[0]，不能用每秒变化的实时值作为锚点。
    const forecastData: [string, number][] = forecast.predictions.map((value, index) => [
      startTime.add(index, 'hour').toISOString(),
      value,
    ])

    return {
      animation: false,
      grid: { top: 28, left: 56, right: 32, bottom: 52 },
      xAxis: {
        type: 'time',
        axisLabel: { formatter: (v: number) => dayjs(v).format('MM-DD HH:mm') },
      },
      yAxis: { type: 'value', name: 'MW' },
      dataZoom: [
        { type: 'inside', minSpan: 5 },
        {
          type: 'slider', height: 24, bottom: 6,
          borderColor: '#2A2A2A', backgroundColor: '#0A0A0A',
          fillerColor: 'rgba(230,195,0,0.12)',
          handleStyle: { color: YELLOW }, textStyle: { color: '#888888' },
        },
      ],
      series: [
        ...(recent.length > 0 ? [{
          id: 'forecast-actual',
          name: '实际',
          type: 'line' as const,
          data: recent.map((d: typeof recent[0]) => [d.time, d.loadMw]),
          smooth: true, symbol: 'none' as const,
          lineStyle: { color: WHITE, width: 1 },
        }] : []),
        {
          id: 'forecast-values',
          name: '预测',
          type: 'line',
          data: forecastData,
          smooth: true, symbol: 'none',
          lineStyle: { color: YELLOW, width: 1.5, type: 'dashed' },
          markLine: {
            silent: true, symbol: 'none',
            lineStyle: { color: YELLOW, type: 'dashed', width: 1 },
            data: [{ xAxis: startTime.toISOString() }],
            label: { formatter: '预测起点', color: YELLOW, fontSize: 11 },
          },
        },
      ],
    }
  }, [hourlyLoadData, forecast, quickRange])

  // ---- 展示统计（前端聚合，每 5 秒更新快照） ----
  const displayStats = useMemo(() => {
    const range = customRange ?? quickToRange(quickRange)
    return calculateDisplayStats(
      hourlyLoadData,
      statsRealtimeSnapshot,
      range[0].valueOf(),
      range[1].valueOf(),
      isLive,
    )
  }, [hourlyLoadData, statsRealtimeSnapshot, quickRange, customRange, isLive])

  // ---- 统计卡片 ----
  const statCards = useMemo(() => {
    const cards = []

    // 当前实时负荷 — 来自 WebSocket 最后一点
    cards.push({
      title: '当前负荷',
      value: fmtMw(liveLoad?.loadMw),
      suffix: ' MW',
      color: RED,
      icon: <ThunderboltOutlined style={{ color: RED, fontSize: 16 }} />,
      sub: liveLoad?.time ? dayjs(liveLoad.time).format('HH:mm:ss') : '--',
    })
    cards.push(
      {
        title: '所选时段峰值',
        value: fmtMw(displayStats.peakLoad),
        suffix: ' MW',
        color: RED,
        sub: displayStats.peakTime
          ? `${dayjs(displayStats.peakTime).format('MM-DD HH:mm')}`
          : '--',
      },
      {
        title: '所选时段谷值',
        value: fmtMw(displayStats.valleyLoad),
        suffix: ' MW',
        color: GREEN,
        sub: displayStats.valleyTime
          ? `${dayjs(displayStats.valleyTime).format('MM-DD HH:mm')}`
          : '--',
      },
      {
        title: '所选时段平均',
        value: fmtMw(displayStats.avgLoad),
        suffix: ' MW',
        color: WHITE,
        sub: isLive
          ? `含实时数据 · ${displayStats.dataPoints} 小时`
          : `按小时聚合 ${displayStats.dataPoints} 个数据点`,
      },
      {
        title: '负荷率',
        value:
          displayStats.loadRate != null && displayStats.loadRate > 0
            ? (displayStats.loadRate * 100).toFixed(1)
            : '--',
        suffix: ' %',
        color: YELLOW,
        sub: isLive ? '含实时数据' : '历史统计',
      },
    )
    // 天气信息
    if (liveLoad?.temperature != null) {
      cards.push({
        title: '当前温度 / 湿度',
        value: `${liveLoad.temperature.toFixed(1)}°C`,
        suffix: '',
        color: WHITE,
        sub: `湿度 ${liveLoad.humidity?.toFixed(0) ?? '--'}%`,
      })
    }
    return cards
  }, [displayStats, liveLoad, isLive])

  // 连接状态和数据延迟
  const connectionStatus = useMemo(() => {
    if (!wsConnected) {
      return { color: RED, text: '断线' }
    }
    const delay = Date.now() - lastRealtimeAt
    if (lastRealtimeAt === 0) {
      return { color: YELLOW, text: '等待数据' }
    }
    if (delay > 5000) {
      return { color: YELLOW, text: `延迟 ${Math.floor(delay / 1000)}s` }
    }
    return { color: GREEN, text: '实时' }
  }, [wsConnected, lastRealtimeAt])

  return (
    <div className="dashboard-root">
      {/* ====== 标题栏 ====== */}
      <div className="dashboard-toolbar">
        <div>
          <h1 style={{ margin: 0, fontSize: 16, fontWeight: 900, letterSpacing: '-0.02em' }}>
            <ThunderboltOutlined style={{ color: RED, marginRight: 6 }} />
            <span style={{ color: WHITE }}>可视化大屏</span>
            <span style={{ color: '#666666', margin: '0 6px', fontSize: 12 }}>//</span>
            <span className="font-mono" style={{ color: '#888888', fontSize: 11, letterSpacing: '0.04em' }}>
              负荷监测 · 预测对比
            </span>
            <span style={{ marginLeft: 12 }}>
              <Tag
                color={connectionStatus.color === RED ? 'red' : connectionStatus.color === YELLOW ? 'gold' : 'green'}
                style={{ fontSize: 11, lineHeight: '18px' }}
              >
                <WifiOutlined style={{ marginRight: 4 }} />
                {connectionStatus.text}
              </Tag>
              {realtimeLoads.length > 0 && (
                <span style={{ color: '#666666', fontSize: 11, marginLeft: 8 }}>
                  实时点: {realtimeLoads.length}
                </span>
              )}
            </span>
          </h1>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Segmented
            value={quickRange}
            onChange={(v) => {
              setQuickRange(v as QuickRange)
              setCustomRange(null)
            }}
            options={[
              { label: '近24小时', value: '24h' },
              { label: '近7天', value: '7d' },
              { label: '近30天', value: '30d' },
            ]}
          />
          <RangePicker
            value={customRange}
            onChange={(dates) => {
              if (dates?.[0] && dates?.[1]) setCustomRange([dates[0], dates[1]])
            }}
            showTime={quickRange === '24h' ? { format: 'HH:mm' } : undefined}
            format={quickRange === '24h' ? 'MM-DD HH:mm' : 'YYYY-MM-DD'}
            placeholder={['开始', '结束']}
            allowClear={!!customRange}
            style={{ width: quickRange === '24h' ? 340 : 280 }}
          />
        </div>
      </div>

      <hr className="brutalist" />

      {/* ====== 统计卡片 ====== */}
      {statCards && (
        <>
          <Row gutter={[8, 8]} style={{ marginBottom: 8 }}>
            {statCards.map((card) => (
              <Col xs={24} sm={12} lg={6} key={card.title}>
                <StatCard {...card} />
              </Col>
            ))}
          </Row>
          <hr className="brutalist" />
        </>
      )}

      {/* ====== 图表区 ====== */}
      <LoadChart
        title="实时负荷曲线"
        option={loadChartOption}
        height={420}
        loading={fetching}
        emptyText="暂无负荷数据"
      />
      <div style={{ height: 12 }} />
      <LoadChart
        title="24h 预测曲线"
        option={predChartOption}
        height={300}
        loading={fetchingForecast}
        emptyText="暂无预测数据"
      />

      <style>{`
        .dashboard-root {
          min-height: calc(100vh - 132px);
          display: flex;
          flex-direction: column;
        }
        .dashboard-toolbar {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          margin-bottom: 8px;
          flex-wrap: wrap;
          gap: 12px;
        }
        @media (min-width: 1920px) {
          .dashboard-root { max-width: 1920px; margin: 0 auto; }
        }
        @media (max-width: 1200px) {
          .dashboard-toolbar { flex-direction: column; }
        }
      `}</style>
    </div>
  )
}

export default Dashboard
