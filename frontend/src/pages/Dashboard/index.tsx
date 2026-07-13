/**
 * 可视化大屏 — 实时负荷仪表盘
 * P0 · Sprint 1 (Day 7 交付)
 *
 * 功能：
 *   - 时间范围选择器 (Segmented 快捷 + RangePicker 自定义)
 *   - 四张统计卡片 (峰值 / 谷值 / 平均 / 负荷率)
 *   - 实时负荷曲线 (ECharts 时间序列) — 支持框选放大 / 滚轮缩放 / 滑块缩放
 *   - 预测 vs 实际对比 (24h 预报叠加) — 支持框选放大 / 滚轮缩放 / 滑块缩放
 *   - 1920×1080 自适应布局
 */
import { useState, useEffect, useCallback, useMemo } from 'react'
import {
  DatePicker,
  Row,
  Col,
  Segmented,
  message,
} from 'antd'
import {
  ThunderboltOutlined,
  ArrowDownOutlined,
  ArrowUpOutlined,
  DashboardOutlined,
  LineChartOutlined,
} from '@ant-design/icons'
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

/** 共用工具箱配置 — 框选放大 + 还原 + 导出图片 */
const SHARED_TOOLBOX: EChartsOption['toolbox'] = {
  right: 12,
  top: 8,
  feature: {
    brush: {
      type: ['rect', 'polygon'],
      title: { rect: '框选放大', polygon: '圈选放大' },
    },
    restore: { title: '还原' },
    saveAsImage: {
      title: '保存图片',
      pixelRatio: 2,
      backgroundColor: '#0a0e27',
    },
  },
  iconStyle: { borderColor: '#8892a4' },
  emphasis: { iconStyle: { borderColor: '#4f8cff' } },
}

/** 共用框选配置 */
const SHARED_BRUSH: EChartsOption['brush'] = {
  toolbox: ['rect', 'polygon'],
  brushLink: 'all',
  outOfBrush: { color: 'rgba(136,146,164,0.15)' },
  throttleType: 'debounce',
  throttleDelay: 300,
}

/** 共用 dataZoom 滑块样式 */
function buildDataZoom(): EChartsOption['dataZoom'] {
  return [
    {
      type: 'inside',
      start: 0,
      end: 100,
      minSpan: 5,
      zoomOnMouseWheel: true,
      moveOnMouseMove: true,
      moveOnMouseWheel: false,
    },
    {
      type: 'slider',
      start: 0,
      end: 100,
      height: 32,
      bottom: 8,
      borderColor: '#1e2a5a',
      backgroundColor: '#0a0e27',
      fillerColor: 'rgba(79,140,255,0.15)',
      handleStyle: { color: '#4f8cff' },
      textStyle: { color: '#8892a4', fontSize: 11 },
      showDetail: true,
      showDataShadow: true,
      dataBackground: {
        lineStyle: { color: 'rgba(79,140,255,0.3)', width: 0.5 },
        areaStyle: { color: 'rgba(79,140,255,0.05)' },
      },
      moveHandleSize: 0,
    },
  ]
}

const Dashboard = () => {
  // ---- 时间范围 ----
  const [quickRange, setQuickRange] = useState<QuickRange>('24h')
  const [customRange, setCustomRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [fetching, setFetching] = useState(false)

  const {
    loadData,
    stats,
    forecast,
    setLoadData,
    setStats,
    setForecast,
  } = useDashboardStore()

  // ---- 获取数据 ----
  const fetchAll = useCallback(async () => {
    const range = customRange ?? quickToRange(quickRange)
    const start = range[0].toISOString()
    const end = range[1].toISOString()

    setFetching(true)
    try {
      const [data, statResult, fcResult] = await Promise.all([
        fetchLoadRange(start, end),
        fetchLoadStats(start, end),
        fetchForecast(),
      ])
      setLoadData(data)
      setStats(statResult)
      setForecast(fcResult)
    } catch {
      message.error('数据加载失败，请确认后端服务已启动')
    } finally {
      setFetching(false)
    }
  }, [quickRange, customRange, setLoadData, setStats, setForecast])

  useEffect(() => {
    fetchAll()
  }, [fetchAll])

  // ---- 负荷曲线 option ----
  const loadChartOption = useMemo<EChartsOption>(() => {
    const points =
      loadData.length > 500 ? loadData.slice(-500) : loadData

    return {
      tooltip: {
        formatter: (params: unknown) => {
          const p = (params as { data: number[] }[])[0]
          if (!p) return ''
          return `
            <div style="font-size:13px">
              <div style="color:#8892a4;margin-bottom:4px">
                ${dayjs(p.data[0]).format('MM-DD HH:mm')}
              </div>
              <div style="color:#4f8cff;font-size:20px;font-weight:600">
                ${(p.data[1] as number).toFixed(1)}
                <span style="font-size:12px;color:#8892a4"> MW</span>
              </div>
            </div>`
        },
      },
      grid: { top: 48, left: 56, right: 32, bottom: 48 },
      toolbox: SHARED_TOOLBOX,
      brush: SHARED_BRUSH,
      xAxis: {
        type: 'time',
        axisLabel: {
          formatter: (v: number) =>
            dayjs(v).format(quickRange === '24h' ? 'HH:mm' : 'MM-DD'),
        },
      },
      dataZoom: buildDataZoom(),
      yAxis: {
        type: 'value',
        name: 'MW',
        nameTextStyle: { color: '#5c6680' },
      },
      series: [
        {
          name: '负荷',
          type: 'line',
          data: points.map((d) => [d.time, d.loadMw]),
          smooth: true,
          symbol: 'none',
          lineStyle: { color: '#4f8cff', width: 2 },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(79,140,255,0.25)' },
                { offset: 1, color: 'rgba(79,140,255,0.02)' },
              ],
            },
          },
        },
      ],
    }
  }, [loadData, quickRange])

  // ---- 预测对比 option ----
  const predChartOption = useMemo<EChartsOption>(() => {
    // 最近 48h 实际数据
    const recent48 = loadData.length > 48 ? loadData.slice(-48) : loadData

    // 构建预测时间轴：从最后一个数据点开始，未来 24h
    const lastTime = recent48.length > 0
      ? dayjs(recent48[recent48.length - 1].time)
      : dayjs()

    // 预测从实际最后一个点开始接续，使两条线视觉连贯
    const predPoints: [string, number][] = []
    if (recent48.length > 0) {
      const last = recent48[recent48.length - 1]
      predPoints.push([last.time, last.loadMw])  // 起锚点 = 最后实际值
    }
    ;(forecast?.predictions ?? []).forEach((v, i) => {
      predPoints.push([lastTime.add(i + 1, 'hour').toISOString(), v])
    })

    // 为实际数据添加标记，便于框选联动区分
    const actualData = recent48.map((d) => [d.time, d.loadMw])

    return {
      tooltip: {
        formatter: (params: unknown) => {
          const items = params as { seriesName: string; data: number[]; color: string }[]
          let html = ''
          for (const item of items) {
            html += `
              <div style="font-size:13px;display:flex;align-items:center;gap:6px">
                <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${item.color}"></span>
                <span style="color:#8892a4">${item.seriesName}</span>
                <span style="color:#e0e6f0;font-weight:600">${(item.data[1] as number).toFixed(1)} MW</span>
              </div>`
          }
          const t = (params as { data: number[] }[])[0]?.data[0]
          return `
            <div style="font-size:13px">
              <div style="color:#8892a4;margin-bottom:6px">${dayjs(t).format('MM-DD HH:mm')}</div>
              ${html}
            </div>`
        },
      },
      grid: { top: 48, left: 56, right: 32, bottom: 48 },
      toolbox: SHARED_TOOLBOX,
      brush: SHARED_BRUSH,
      xAxis: {
        type: 'time',
        axisLabel: { formatter: (v: number) => dayjs(v).format('MM-DD HH:mm') },
      },
      dataZoom: buildDataZoom(),
      yAxis: {
        type: 'value',
        name: 'MW',
        nameTextStyle: { color: '#5c6680' },
      },
      series: [
        {
          name: '实际负荷',
          type: 'line',
          data: actualData,
          smooth: true,
          symbol: 'none',
          lineStyle: { color: '#4f8cff', width: 2 },
        },
        {
          name: '预测负荷',
          type: 'line',
          data: predPoints,
          smooth: true,
          symbol: 'circle',
          symbolSize: 4,
          lineStyle: { color: '#fbbf24', width: 2, type: 'dashed' },
          itemStyle: { color: '#fbbf24' },
        },
      ],
    }
  }, [loadData, forecast])

  // ---- 统计卡片数据 ----
  const statCards = useMemo(() => {
    if (!stats) return null
    return [
      {
        title: '峰值负荷',
        value: fmtMw(stats.peakLoad),
        suffix: ' MW',
        color: '#f87171',
        icon: <ArrowUpOutlined style={{ color: '#f87171', fontSize: 16 }} />,
        sub: stats.peakTime ? dayjs(stats.peakTime).format('MM-DD HH:mm') : '--',
      },
      {
        title: '谷值负荷',
        value: fmtMw(stats.valleyLoad),
        suffix: ' MW',
        color: '#34d399',
        icon: <ArrowDownOutlined style={{ color: '#34d399', fontSize: 16 }} />,
        sub: stats.valleyTime ? dayjs(stats.valleyTime).format('MM-DD HH:mm') : '--',
      },
      {
        title: '平均负荷',
        value: fmtMw(stats.avgLoad),
        suffix: ' MW',
        color: '#4f8cff',
        icon: <DashboardOutlined style={{ color: '#4f8cff', fontSize: 16 }} />,
        sub: `${stats.dataPoints} 个数据点`,
      },
      {
        title: '负荷率',
        value: stats.loadRate != null ? (stats.loadRate * 100).toFixed(1) : '--',
        suffix: ' %',
        color: '#fbbf24',
        icon: <LineChartOutlined style={{ color: '#fbbf24', fontSize: 16 }} />,
        sub: 'avg / peak',
      },
    ]
  }, [stats])

  return (
    <div className="dashboard-root">
      {/* ====== 顶栏：标题 + 时间选择器 ====== */}
      <div className="dashboard-toolbar">
        <h1 className="dashboard-title">
          <ThunderboltOutlined style={{ color: '#4f8cff', marginRight: 10 }} />
          可视化大屏
        </h1>

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

      {/* ====== 统计卡片行 ====== */}
      {statCards && (
        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          {statCards.map((card) => (
            <Col xs={24} sm={12} lg={6} key={card.title}>
              <StatCard {...card} />
            </Col>
          ))}
        </Row>
      )}

      {/* ====== 图表区 ====== */}
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <LoadChart
            title="实时负荷曲线"
            option={loadChartOption}
            height={420}
            loading={fetching}
            emptyText="所选时间范围内无负荷数据"
          />
        </Col>
        <Col xs={24} lg={12}>
          <LoadChart
            title="预测 vs 实际对比"
            option={predChartOption}
            height={420}
            loading={fetching}
            emptyText="暂无预测数据"
          />
        </Col>
      </Row>

      {/* ====== 内联自适应样式 ====== */}
      <style>{`
        .dashboard-root {
          min-height: calc(100vh - 136px); /* 减去 header + content padding */
          display: flex;
          flex-direction: column;
        }
        .dashboard-toolbar {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 20px;
          flex-wrap: wrap;
          gap: 12px;
        }
        .dashboard-title {
          margin: 0;
          color: #e0e6f0;
          font-size: 22px;
          font-weight: 600;
          white-space: nowrap;
        }
        /* 1920×1080 大屏优化 */
        @media (min-width: 1920px) {
          .dashboard-root {
            max-width: 1920px;
            margin: 0 auto;
          }
        }
        @media (max-width: 1200px) {
          .dashboard-toolbar {
            flex-direction: column;
            align-items: flex-start;
          }
        }
      `}</style>
    </div>
  )
}

export default Dashboard
