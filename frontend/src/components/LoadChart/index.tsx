/**
 * LoadChart — 可复用 ECharts 图表容器
 * 封装 loading / empty / resize，统一暗色大屏主题
 */
import { useRef, useMemo } from 'react'
import ReactECharts from 'echarts-for-react'
import { Card, Spin, Empty } from 'antd'
import type { EChartsOption } from 'echarts'

interface LoadChartProps {
  /** 图表标题 */
  title?: string
  /** ECharts 配置项 */
  option: EChartsOption
  /** 图表高度 (px)，默认 360 */
  height?: number
  /** 是否加载中 */
  loading?: boolean
  /** 数据为空时的提示文案 */
  emptyText?: string
}

/** 暗色大屏 ECharts 基础主题 */
const DARK_THEME: EChartsOption = {
  backgroundColor: 'transparent',
  grid: { left: 56, right: 32, top: 16, bottom: 36 },
  tooltip: {
    trigger: 'axis',
    backgroundColor: '#141a4a',
    borderColor: '#1e2a5a',
    textStyle: { color: '#e0e6f0', fontSize: 13 },
  },
  toolbox: {
    iconStyle: { borderColor: '#8892a4' },
    emphasis: { iconStyle: { borderColor: '#4f8cff' } },
  },
  legend: {
    textStyle: { color: '#8892a4' },
    top: 0,
  },
  xAxis: {
    axisLine: { lineStyle: { color: '#1e2a5a' } },
    axisTick: { lineStyle: { color: '#1e2a5a' } },
    axisLabel: { color: '#8892a4' },
    splitLine: { show: false },
  },
  yAxis: {
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: '#8892a4' },
    splitLine: { lineStyle: { color: '#162050', type: 'dashed' } },
  },
}

const LoadChart = ({
  title,
  option,
  height = 360,
  loading = false,
  emptyText = '暂无数据',
}: LoadChartProps) => {
  const chartRef = useRef<ReactECharts>(null)

  const mergedOption = useMemo(() => {
    const merged = { ...DARK_THEME, ...option }
    // 深度合并子对象，避免被外部 option 覆盖
    merged.grid = { ...DARK_THEME.grid, ...option.grid }
    merged.tooltip = { ...DARK_THEME.tooltip, ...option.tooltip }
    return merged
  }, [option])

  const hasData = useMemo(() => {
    const s = option.series
    if (!s) return false
    if (Array.isArray(s)) return s.length > 0
    return true
  }, [option.series])

  const isEmpty = !loading && !hasData

  return (
    <Card title={title} bodyStyle={{ padding: isEmpty ? 40 : 12 }}>
      {loading ? (
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            height,
          }}
        >
          <Spin size="large" />
        </div>
      ) : isEmpty ? (
        <Empty
          description={<span style={{ color: '#5c6680' }}>{emptyText}</span>}
          style={{ margin: 'auto' }}
        />
      ) : (
        <ReactECharts
          ref={chartRef}
          option={mergedOption}
          style={{ height, width: '100%' }}
          notMerge
          lazyUpdate
        />
      )}
    </Card>
  )
}

export default LoadChart
