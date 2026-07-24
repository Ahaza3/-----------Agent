import { describe, expect, it, beforeEach } from 'vitest'
import useDashboardStore, { normalizeRealtimePoint } from '../stores/useDashboardStore'
import { buildLoadSeriesSegments } from '../pages/Dashboard/loadSeries'
import type { RealtimeLoadPoint } from '../types/load'

function telemetry(overrides: Partial<RealtimeLoadPoint> = {}): RealtimeLoadPoint {
  return {
    timestamp: 1_000,
    sequence: 1,
    loadMw: 900,
    temperature: 25,
    humidity: 60,
    source: 'MOCK',
    dataSource: 'MOCK_REALTIME',
    sourceInstanceId: 'instance-a',
    qualityCode: 'GOOD',
    freshnessStatus: 'FRESH',
    ...overrides,
  }
}

describe('realtime telemetry quality', () => {
  beforeEach(() => useDashboardStore.getState().reset())

  it('marks missing quality fields as LEGACY_UNKNOWN without breaking the page', () => {
    const normalized = normalizeRealtimePoint({
      timestamp: 1_000, sequence: 1, loadMw: 900, temperature: null, humidity: null, source: 'MOCK',
    })
    expect(normalized.qualityCode).toBe('LEGACY_UNKNOWN')
    expect(normalized.freshnessStatus).toBe('LEGACY_UNKNOWN')
  })

  it('deduplicates REST and WebSocket samples by source instance and sequence', () => {
    const store = useDashboardStore.getState()
    store.mergeRealtimeLoads([telemetry()])
    store.appendRealtimeLoad(telemetry({ timestamp: 2_000 }))
    expect(useDashboardStore.getState().realtimeLoads).toHaveLength(1)
  })

  it('does not add BAD points to the curve but retains estimated and late points with labels', () => {
    const store = useDashboardStore.getState()
    store.appendRealtimeLoad(telemetry({ qualityCode: 'BAD' }))
    store.appendRealtimeLoad(telemetry({ sequence: 2, qualityCode: 'ESTIMATED', estimated: true }))
    store.appendRealtimeLoad(telemetry({ sequence: 3, qualityCode: 'LATE', timestamp: 3_000 }))
    const points = useDashboardStore.getState().realtimeLoads
    expect(points.map((point) => point.qualityCode)).toEqual(['ESTIMATED', 'LATE'])
    expect(buildLoadSeriesSegments([], points, true).realtimeData).toHaveLength(2)
  })

  it('keeps reconnected snapshot points in observation-time order and preserves stale state', () => {
    const store = useDashboardStore.getState()
    store.mergeRealtimeLoads([
      telemetry({ sequence: 2, timestamp: 2_000, freshnessStatus: 'STALE' }),
      telemetry({ sequence: 1, timestamp: 1_000, sourceInstanceId: 'instance-b' }),
    ])
    const points = useDashboardStore.getState().realtimeLoads
    expect(points.map((point) => point.timestamp)).toEqual([1_000, 2_000])
    expect(points[1].freshnessStatus).toBe('STALE')
  })
})
