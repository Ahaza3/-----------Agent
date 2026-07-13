/**
 * LoadChart — Brutalist CRT 风格 ECharts 容器
 * 零渐变 / 零阴影 / 终端配色
 */
import { useRef, useMemo } from 'react'
import ReactECharts from 'echarts-for-react'
import { Card, Spin, Empty } from 'antd'
import type { EChartsOption } from 'echarts'

interface LoadChartProps {
  title?: string
  option: EChartsOption
  height?: number
  loading?: boolean
  emptyText?: string
}

/** Tactical Telemetry 图表主题 */
const CRT_THEME: EChartsOption = {
  backgroundColor: 'transparent',
  grid: { left: 52, right: 28, top: 12, bottom: 36 },
  tooltip: {
    trigger: 'axis',
    backgroundColor: '#0E0E0E',
    borderColor: '#FF2A2A',
    borderWidth: 1,
    textStyle: {
      color: '#EAEAEA',
      fontSize: 12,
      fontFamily:
        "'JetBrains Mono', 'IBM Plex Mono', 'Consolas', monospace",
    },
  },
  legend: {
    textStyle: {
      color: '#888888',
      fontFamily:
        "'JetBrains Mono', 'IBM Plex Mono', 'Consolas', monospace",
      fontSize: 11,
    },
    top: 0,
  },
  xAxis: {
    axisLine: { lineStyle: { color: '#2A2A2A', width: 1 } },
    axisTick: { lineStyle: { color: '#2A2A2A' } },
    axisLabel: {
      color: '#888888',
      fontFamily:
        "'JetBrains Mono', 'IBM Plex Mono', 'Consolas', monospace",
      fontSize: 10,
    },
    splitLine: { show: false },
  },
  yAxis: {
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: {
      color: '#888888',
      fontFamily:
        "'JetBrains Mono', 'IBM Plex Mono', 'Consolas', monospace",
      fontSize: 10,
    },
    splitLine: { lineStyle: { color: '#1A1A1A', type: 'solid', width: 1 } },
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
    const merged = { ...CRT_THEME, ...option }
    merged.grid = { ...CRT_THEME.grid, ...option.grid }
    merged.tooltip = { ...CRT_THEME.tooltip, ...option.tooltip }
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
    <Card
      title={
        title ? (
          <span
            className="font-mono"
            style={{
              fontSize: 11,
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              color: '#AAAAAA',
            }}
          >
            &gt;&gt; {title} &lt;&lt;
          </span>
        ) : undefined
      }
      bodyStyle={{ padding: isEmpty ? 40 : 8 }}
      style={{ border: '1px solid #2A2A2A' }}
    >
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
          description={
            <span className="font-mono" style={{ color: '#666666', fontSize: 12 }}>
              // {emptyText} //
            </span>
          }
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
