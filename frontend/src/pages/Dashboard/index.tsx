/**
 * 可视化大屏 — Brutalist CRT Terminal 风格
 * P0 · Sprint 1
 *
 * 默认展示实时负荷曲线，点击"预测"后加载预测对比图
 */
import { useState, useEffect, useCallback, useMemo } from 'react'
import { DatePicker, Row, Col, Segmented, Button, message } from 'antd'
import { ThunderboltOutlined, LineChartOutlined } from '@ant-design/icons'
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
  const [showForecast, setShowForecast] = useState(false)

  const {
    loadData,
    stats,
    forecast,
    setLoadData,
    setStats,
    setForecast,
  } = useDashboardStore()

  // ---- 基础数据（不包含预测） ----
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

  // ---- 点击预测按钮 ----
  const handleForecast = useCallback(async () => {
    if (showForecast) {
      setShowForecast(false)
      return
    }
    setFetchingForecast(true)
    try {
      const fc = await fetchForecast()
      setForecast(fc)
      setShowForecast(true)
    } catch {
      message.error('预测数据加载失败')
    } finally {
      setFetchingForecast(false)
    }
  }, [showForecast, setForecast])

  // ---- 负荷曲线（预测开启时叠加预测线） ----
  const loadChartOption = useMemo<EChartsOption>(() => {
    const points = loadData.length > 500 ? loadData.slice(-500) : loadData
    const baseOption: EChartsOption = {
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
          name: showForecast ? '实际负荷' : '负荷',
          type: 'line',
          data: points.map((d) => [d.time, d.loadMw]),
          smooth: true,
          symbol: 'none',
          lineStyle: { color: WHITE, width: 1.5 },
          areaStyle: { color: 'rgba(255,42,42,0.06)' },
        },
      ],
    }

    // 预测开启时：追加预测 series + 标记线
    if (showForecast && forecast && loadData.length > 0) {
      const last = loadData[loadData.length - 1]
      const lastTime = dayjs(last.time)
      const lastVal = last.loadMw

      const forecastData: [string, number][] = [
        [lastTime.toISOString(), lastVal],
      ]
      forecast.predictions.forEach((v, i) => {
        forecastData.push([lastTime.add(i + 1, 'hour').toISOString(), v])
      })

      baseOption.series = [
        ...(baseOption.series as object[]),
        {
          name: '预测负荷',
          type: 'line',
          data: forecastData,
          smooth: true,
          symbol: 'none',
          lineStyle: { color: YELLOW, width: 1.5, type: 'dashed' },
          // 标记预测起点
          markLine: {
            silent: true,
            symbol: 'none',
            lineStyle: { color: YELLOW, type: 'dashed', width: 1 },
            data: [{ xAxis: lastTime.toISOString() }],
            label: {
              formatter: '预测开始',
              color: YELLOW,
              fontSize: 11,
            },
          },
        },
      ]
    }

    return baseOption
  }, [loadData, quickRange, showForecast, forecast])

  // ---- 统计卡片 ----
  const statCards = useMemo(() => {
    if (!stats) return null
    return [
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
    ]
  }, [stats])

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
          <Button
            type={showForecast ? 'primary' : 'default'}
            icon={<LineChartOutlined />}
            onClick={handleForecast}
            loading={fetchingForecast}
            style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}
          >
            {showForecast ? '隐藏预测' : '预测'}
          </Button>
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
        title={showForecast ? '实时负荷曲线 · 预测对比' : '实时负荷曲线'}
        option={loadChartOption}
        height={520}
        loading={fetching}
        emptyText="暂无负荷数据"
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
