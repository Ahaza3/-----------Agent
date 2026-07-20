import { describe, expect, it } from 'vitest'
import { buildLoadSeriesSegments } from '../pages/Dashboard/loadSeries'
import type { LoadData, RealtimeLoadPoint } from '../types/load'

function history(time: string, loadMw: number, dataSource: string): LoadData {
  return {
    id: 1,
    time,
    loadMw,
    temperature: 25,
    humidity: 60,
    isHoliday: 0,
    hour: new Date(time).getHours(),
    dayOfWeek: 0,
    month: 7,
    createdAt: time,
    dataSource,
  }
}

function realtime(time: string, loadMw: number, sequence: number): RealtimeLoadPoint {
  return {
    timestamp: new Date(time).getTime(),
    sequence,
    loadMw,
    temperature: 25,
    humidity: 60,
    source: 'MOCK',
  }
}

describe('buildLoadSeriesSegments', () => {
  it('lets realtime samples replace overlapping recovered hourly points', () => {
    const result = buildLoadSeriesSegments([
      history('2026-07-17T21:00:00+08:00', 860, 'RECOVERED_SIMULATION'),
      history('2026-07-17T22:00:00+08:00', 810, 'RECOVERED_SIMULATION'),
      history('2026-07-17T23:00:00+08:00', 724, 'RECOVERED_SIMULATION'),
    ], [
      realtime('2026-07-17T22:46:00+08:00', 809, 1),
      realtime('2026-07-17T23:00:01+08:00', 807, 2),
    ], true)

    expect(result.recoveryData.map(([time]) => time)).not.toContain('2026-07-17T23:00:00+08:00')
    expect(result.bridgeData[0]).toEqual(['2026-07-17T22:00:00+08:00', 810])
    expect(result.bridgeData.at(-1)).toEqual([new Date('2026-07-17T22:46:00+08:00').toISOString(), 809])
    expect(result.realtimeData[0][1]).toBe(809)
  })

  it('keeps the current hourly point when realtime begins after it', () => {
    const result = buildLoadSeriesSegments([
      history('2026-07-17T23:00:00+08:00', 724, 'RECOVERED_SIMULATION'),
    ], [
      realtime('2026-07-17T23:05:00+08:00', 726, 1),
    ], true)

    expect(result.recoveryData).toEqual([['2026-07-17T23:00:00+08:00', 724]])
    expect(result.bridgeData).toEqual([
      ['2026-07-17T23:00:00+08:00', 724],
      [new Date('2026-07-17T23:05:00+08:00').toISOString(), 726],
    ])
  })

  it('does not bridge a long interval with a misleading straight line', () => {
    const result = buildLoadSeriesSegments([
      history('2026-07-17T20:00:00+08:00', 880, 'MOCK_HISTORY'),
    ], [
      realtime('2026-07-17T23:00:00+08:00', 740, 1),
    ], true)

    expect(result.realtimeData).toHaveLength(1)
    expect(result.realtimeData[0][1]).toBe(740)
    expect(result.bridgeData).toEqual([])
  })

  it('bridges recovered hourly data to realtime even across a stale interval', () => {
    const result = buildLoadSeriesSegments([
      history('2026-07-17T20:00:00+08:00', 880, 'RECOVERED_SIMULATION'),
    ], [
      realtime('2026-07-17T23:00:00+08:00', 740, 1),
    ], true)

    expect(result.bridgeData[0]).toEqual(['2026-07-17T20:00:00+08:00', 880])
    expect(result.bridgeData.at(-1)).toEqual([new Date('2026-07-17T23:00:00+08:00').toISOString(), 740])
    expect(result.bridgeData.length).toBeGreaterThan(2)
  })

  it('shares a real boundary when realtime begins within two minutes', () => {
    const result = buildLoadSeriesSegments([
      history('2026-07-17T23:00:00+08:00', 724, 'RECOVERED_SIMULATION'),
    ], [
      realtime('2026-07-17T23:01:00+08:00', 726, 1),
    ], true)

    expect(result.bridgeData).toEqual([])
    expect(result.realtimeData[0]).toEqual(['2026-07-17T23:00:00+08:00', 724])
  })

  it('adds smooth bridge points instead of a chart-only realtime hour anchor', () => {
    const result = buildLoadSeriesSegments([
      history('2026-07-17T08:00:00+08:00', 880, 'RECOVERED_SIMULATION'),
    ], [
      realtime('2026-07-17T09:46:00+08:00', 740, 1),
    ], true)

    expect(result.realtimeData[0]).toEqual([
      new Date('2026-07-17T09:46:00+08:00').toISOString(),
      740,
    ])
    expect(result.bridgeData[0]).toEqual(['2026-07-17T08:00:00+08:00', 880])
    expect(result.bridgeData.at(-1)).toEqual(result.realtimeData[0])
    expect(result.bridgeData.length).toBeGreaterThan(2)
    expect(result.bridgeData.some(([time]) => time === new Date('2026-07-17T09:00:00+08:00').toISOString())).toBe(true)
  })

  it('separates historical and recovered sources with a shared boundary', () => {
    const result = buildLoadSeriesSegments([
      history('2026-07-17T20:00:00+08:00', 900, 'MOCK_HISTORY'),
      history('2026-07-17T21:00:00+08:00', 860, 'RECOVERED_SIMULATION'),
      history('2026-07-17T22:00:00+08:00', 810, 'RECOVERED_SIMULATION'),
    ], [], false)

    expect(result.historyData).toEqual([['2026-07-17T20:00:00+08:00', 900]])
    expect(result.recoveryData[0]).toEqual(['2026-07-17T20:00:00+08:00', 900])
  })
})
