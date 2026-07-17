import type { LoadData, RealtimeLoadPoint } from '../../types/load'

export type LoadChartPoint = [string, number]

interface LoadSeriesSegments {
  historyData: LoadChartPoint[]
  recoveryData: LoadChartPoint[]
  bridgeData: LoadChartPoint[]
  realtimeData: LoadChartPoint[]
}

const MAX_BRIDGE_GAP_MS = 90 * 60 * 1000
const MAX_SHARED_BOUNDARY_GAP_MS = 2 * 60 * 1000

function historicalPoint(point: LoadData): LoadChartPoint {
  return [point.time, point.loadMw]
}

/**
 * Realtime samples take precedence over generated hourly points in overlapping
 * ranges. A shared boundary point changes line color without inventing values.
 */
export function buildLoadSeriesSegments(
  hourlyPoints: LoadData[],
  realtimePoints: RealtimeLoadPoint[],
  includeRealtime: boolean,
  maxRealtimePoints = 3600,
): LoadSeriesSegments {
  const recentRealtime = includeRealtime
    ? realtimePoints
        .slice(-maxRealtimePoints)
        .filter((point) => Number.isFinite(point.timestamp) && Number.isFinite(point.loadMw))
        .sort((a, b) => a.timestamp - b.timestamp)
    : []

  const firstRealtimeTime = recentRealtime[0]?.timestamp
  const visibleHourly = firstRealtimeTime == null
    ? hourlyPoints
    : hourlyPoints.filter((point) => new Date(point.time).getTime() < firstRealtimeTime)

  const firstRecoveryIndex = visibleHourly.findIndex(
    (point) => point.dataSource === 'RECOVERED_SIMULATION',
  )

  let historyData: LoadChartPoint[]
  let recoveryData: LoadChartPoint[]

  if (firstRecoveryIndex < 0) {
    historyData = visibleHourly.map(historicalPoint)
    recoveryData = []
  } else {
    historyData = visibleHourly.slice(0, firstRecoveryIndex).map(historicalPoint)
    const recoveryStart = Math.max(0, firstRecoveryIndex - 1)
    recoveryData = visibleHourly.slice(recoveryStart).map(historicalPoint)
  }

  const realtimeData = recentRealtime.map<LoadChartPoint>((point) => [
    new Date(point.timestamp).toISOString(),
    point.loadMw,
  ])
  let bridgeData: LoadChartPoint[] = []

  const boundary = visibleHourly[visibleHourly.length - 1]
  if (boundary && recentRealtime.length > 0) {
    const boundaryTime = new Date(boundary.time).getTime()
    const gap = recentRealtime[0].timestamp - boundaryTime
    if (gap >= 0 && gap <= MAX_SHARED_BOUNDARY_GAP_MS) {
      realtimeData.unshift(historicalPoint(boundary))
    } else if (gap > MAX_SHARED_BOUNDARY_GAP_MS && gap <= MAX_BRIDGE_GAP_MS) {
      bridgeData = [historicalPoint(boundary), realtimeData[0]]
    }
  }

  return { historyData, recoveryData, bridgeData, realtimeData }
}
