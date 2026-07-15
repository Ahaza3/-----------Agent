/**
 * 可视化大屏 — Brutalist CRT Terminal 风格
 * P0 · Sprint 1
 *
 * 默认展示实时负荷曲线，点击"预测"后加载预测对比图
 */
import { useState, useEffect, useCallback, useMemo } from 'react'
import { DatePicker, Row, Col, Segmented, message } from 'antd'
import { ThunderboltOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import type { EChartsOption } from 'echarts'
import StatCard from '../../components/StatCard'
import LoadChart from '../../components/LoadChart'
import { fetchLoadRange, fetchLoadStats } from '../../services/dataApi'
import { fetchForecast } from '../../services/predictApi'
import useDashboardStore from '../../stores/useDashboardStore'

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

function fmtMw(v: number | undefined | null): string {
  if (v == null) return '--'
  return v.toFixed(1)
}

const RED = '#FF2A2A'
const YELLOW = '#E6C300'
const GREEN = '#4AF626'
const WHITE = '#EAEAEA'

const Dashboard = () => {
  const [quickRange, setQuickRange] = useState<QuickRange>('24h')
  const [customRange, setCustomRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [fetching, setFetching] = useState(false)
  const [fetchingForecast, setFetchingForecast] = useState(false)

  const {
    loadData,
    liveLoad,
    stats,
    forecast,
    setLoadData,
    setStats,
    setForecast,
  } = useDashboardStore()

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

  // 每 30 秒静默刷新图表数据（不显示 loading）
  useEffect(() => {
    const timer = setInterval(() => {
      const range = customRange ?? quickToRange(quickRange)
      Promise.all([
        fetchLoadRange(range[0].toISOString(), range[1].toISOString()),
        fetchLoadStats(range[0].toISOString(), range[1].toISOString()),
      ]).then(([data, st]) => {
        setLoadData(data)
        setStats(st)
      }).catch(() => {})
    }, 30_000)
    return () => clearInterval(timer)
  }, [quickRange, customRange, setLoadData, setStats])

  // ---- 首次加载时拉预测 ----
  useEffect(() => {
    setFetchingForecast(true)
    fetchForecast().then(setForecast).catch(() => {}).finally(() => setFetchingForecast(false))
  }, [setForecast])

  // ---- 实时负荷曲线 ----
  const loadChartOption = useMemo<EChartsOption>(() => {
    const points = loadData.length > 500 ? loadData.slice(-500) : loadData
    return {
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
        { type: 'inside', start: 0, end: 100, minSpan: 5 },
        {
          type: 'slider', start: 0, end: 100, height: 24, bottom: 6,
          borderColor: '#2A2A2A', backgroundColor: '#0A0A0A',
          fillerColor: 'rgba(255,42,42,0.12)',
          handleStyle: { color: RED }, textStyle: { color: '#888888' },
        },
      ],
      series: [
        {
          name: '负荷',
          type: 'line',
          data: points.map((d) => [d.time, d.loadMw]),
          smooth: true, symbol: 'none',
          lineStyle: { color: WHITE, width: 1.5 },
          areaStyle: { color: 'rgba(255,42,42,0.06)' },
        },
      ],
    }
  }, [loadData, quickRange])

  // ---- 预测曲线（独立图表） ----
  const predChartOption = useMemo<EChartsOption>(() => {
    if (!forecast || loadData.length === 0) {
      return {
        series: [],
      }
    }
    const recent48 = loadData.length > 48 ? loadData.slice(-48) : loadData
    const last = recent48[recent48.length - 1]
    const lastTime = dayjs(last.time)

    const forecastData: [string, number][] = [[lastTime.toISOString(), last.loadMw]]
    forecast.predictions.forEach((v, i) => {
      forecastData.push([lastTime.add(i + 1, 'hour').toISOString(), v])
    })

    return {
      grid: { top: 28, left: 56, right: 32, bottom: 52 },
      xAxis: {
        type: 'time',
        axisLabel: { formatter: (v: number) => dayjs(v).format('MM-DD HH:mm') },
      },
      yAxis: { type: 'value', name: 'MW' },
      dataZoom: [
        { type: 'inside', start: 0, end: 100, minSpan: 5 },
        {
          type: 'slider', start: 0, end: 100, height: 24, bottom: 6,
          borderColor: '#2A2A2A', backgroundColor: '#0A0A0A',
          fillerColor: 'rgba(230,195,0,0.12)',
          handleStyle: { color: YELLOW }, textStyle: { color: '#888888' },
        },
      ],
      series: [
        {
          name: '实际',
          type: 'line',
          data: recent48.map((d) => [d.time, d.loadMw]),
          smooth: true, symbol: 'none',
          lineStyle: { color: WHITE, width: 1 },
        },
        {
          name: '预测',
          type: 'line',
          data: forecastData,
          smooth: true, symbol: 'none',
          lineStyle: { color: YELLOW, width: 1.5, type: 'dashed' },
          markLine: {
            silent: true, symbol: 'none',
            lineStyle: { color: YELLOW, type: 'dashed', width: 1 },
            data: [{ xAxis: lastTime.toISOString() }],
            label: { formatter: '现在', color: YELLOW, fontSize: 11 },
          },
        },
      ],
    }
  }, [loadData, forecast])

  // ---- 统计卡片 ----
  const statCards = useMemo(() => {
    if (!stats) return null
    const cards = []
    // 当前实时负荷（WebSocket 推送）
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
        title: '峰值负荷',
        value: fmtMw(stats.peakLoad),
        suffix: ' MW',
        color: RED,
        sub: stats.peakTime
          ? `${dayjs(stats.peakTime).format('MM-DD HH:mm')}`
          : '--',
      },
      {
        title: '谷值负荷',
        value: fmtMw(stats.valleyLoad),
        suffix: ' MW',
        color: GREEN,
        sub: stats.valleyTime
          ? `${dayjs(stats.valleyTime).format('MM-DD HH:mm')}`
          : '--',
      },
      {
        title: '平均负荷',
        value: fmtMw(stats.avgLoad),
        suffix: ' MW',
        color: WHITE,
        sub: `共 ${stats.dataPoints} 个数据点`,
      },
      {
        title: '负荷率',
        value:
          stats.loadRate != null ? (stats.loadRate * 100).toFixed(1) : '--',
        suffix: ' %',
        color: YELLOW,
        sub: '均值 / 峰值',
      },
    )
    return cards
  }, [stats, liveLoad])

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
