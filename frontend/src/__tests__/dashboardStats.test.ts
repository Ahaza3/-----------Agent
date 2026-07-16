/**
 * dashboardStats 纯函数测试
 */
import { describe, it, expect } from 'vitest'
import { calculateDisplayStats } from '../pages/Dashboard/dashboardStats'
import type { LoadData, RealtimeLoadPoint } from '../types/load'

function h(time: string, loadMw: number): LoadData {
  return {
    id: 0, time, loadMw, temperature: 25, humidity: 60,
    isHoliday: 0, hour: 10, dayOfWeek: 2, month: 7, createdAt: time,
  }
}

function rt(epochMs: number, loadMw: number, seq = 0): RealtimeLoadPoint {
  return { timestamp: epochMs, sequence: seq, loadMw, temperature: 25, humidity: 60, source: 'MOCK' }
}

// Helper: create epoch ms for Asia/Shanghai date-time (UTC+8)
function shTs(dateStr: string): number {
  return new Date(dateStr + '+08:00').getTime()
}

const RANGE_START = shTs('2026-07-15T09:00:00')
const RANGE_END = shTs('2026-07-15T13:00:00')

describe('calculateDisplayStats', () => {
  it('1. 只有小时历史数据时统计正确', () => {
    const hist = [
      h('2026-07-15T10:00:00', 800),
      h('2026-07-15T11:00:00', 900),
      h('2026-07-15T12:00:00', 850),
    ]
    const result = calculateDisplayStats(hist, [], RANGE_START, RANGE_END, false)
    expect(result.peakLoad).toBe(900)
    expect(result.valleyLoad).toBe(800)
    expect(result.avgLoad).toBe(850)
    expect(result.dataPoints).toBe(3)
    expect(result.loadRate).toBeCloseTo(850 / 900, 4)
    expect(result.stdDeviation).toBeGreaterThan(0)
  })

  it('2. 同一小时 100 个实时点只形成一个平均样本', () => {
    const hist = [h('2026-07-15T10:00:00', 800), h('2026-07-15T11:00:00', 900)]
    const hour11 = shTs('2026-07-15T11:00:00')
    const realtime: RealtimeLoadPoint[] = []
    for (let i = 0; i < 100; i++) {
      realtime.push(rt(hour11 + i * 1000, 1000 + i))
    }
    const result = calculateDisplayStats(hist, realtime, RANGE_START, RANGE_END, true)
    // 小时代表值：10:00=800, 11:00=avg(1000..1099)=1049.5
    // dataPoints 应该是 2（两个小时桶），不是 101
    expect(result.dataPoints).toBe(2)
    const expectedAvg = (800 + 1049.5) / 2
    expect(result.avgLoad).toBeCloseTo(expectedAvg, 1)
  })

  it('3. 实时小时平均值替换同小时 historical，不重复计数', () => {
    const hist = [
      h('2026-07-15T10:00:00', 800),
      h('2026-07-15T11:00:00', 900),
    ]
    const hour10 = shTs('2026-07-15T10:30:00')
    const realtime = [rt(hour10, 950), rt(hour10 + 60000, 1050)]
    const result = calculateDisplayStats(hist, realtime, RANGE_START, RANGE_END, true)
    // 10:00 小时有实时点 avg(950,1050)=1000，替换 historical 800
    // 11:00 只有 historical 900
    // 小时代表值：1000, 900
    // 不应有 3 个样本
    expect(result.dataPoints).toBe(2)
    expect(result.avgLoad).toBeCloseTo(950, 1)
  })

  it('4. 实时瞬时 1240MW 能成为峰值', () => {
    const hist = [h('2026-07-15T10:00:00', 800), h('2026-07-15T11:00:00', 900)]
    const hour11 = shTs('2026-07-15T11:30:00')
    const realtime = [rt(hour11, 1000), rt(hour11 + 1000, 1240), rt(hour11 + 2000, 1210)]
    const result = calculateDisplayStats(hist, realtime, RANGE_START, RANGE_END, true)
    // 峰值应该是 1240（实时瞬时值），不是小时平均值
    expect(result.peakLoad).toBe(1240)
    // 谷值仍是 800
    expect(result.valleyLoad).toBe(800)
  })

  it('5. 实时负荷恢复到 940MW 后，之前的 1240MW 仍是所选区间峰值', () => {
    const hist = [h('2026-07-15T10:00:00', 800), h('2026-07-15T11:00:00', 900)]
    const hour10 = shTs('2026-07-15T10:05:00')
    const hour11 = shTs('2026-07-15T11:30:00')
    const realtime = [
      rt(hour10, 1200),
      rt(hour11, 940), // 后来降回 940
    ]
    const result = calculateDisplayStats(hist, realtime, RANGE_START, RANGE_END, true)
    expect(result.peakLoad).toBe(1200)
  })

  it('6. includeRealtime=false 时完全忽略实时点', () => {
    const hist = [h('2026-07-15T10:00:00', 800)]
    const hour10 = shTs('2026-07-15T10:30:00')
    const realtime = [rt(hour10, 1500)]
    const result = calculateDisplayStats(hist, realtime, RANGE_START, RANGE_END, false)
    expect(result.peakLoad).toBe(800) // 不是 1500
    expect(result.dataPoints).toBe(1)
  })

  it('7. 区间外实时点被排除', () => {
    const hist = [h('2026-07-15T10:00:00', 800)]
    // 14:00 在区间外 (range end = 13:00)
    const hour14 = shTs('2026-07-15T14:00:00')
    const realtime = [rt(hour14, 9999)]
    const result = calculateDisplayStats(hist, realtime, RANGE_START, RANGE_END, true)
    expect(result.peakLoad).toBe(800)
  })

  it('8. 峰值、谷值时间正确', () => {
    const hist = [h('2026-07-15T10:00:00', 800)]
    const hour10 = shTs('2026-07-15T10:15:00')
    const realtime = [rt(hour10, 1240)]
    const result = calculateDisplayStats(hist, realtime, RANGE_START, RANGE_END, true)
    expect(result.peakLoad).toBe(1240)
    expect(result.peakTime).toBeTruthy()
    // 峰值时间应该接近实时点时间
    const peakDate = new Date(result.peakTime!)
    expect(Math.abs(peakDate.getTime() - hour10)).toBeLessThan(1000)
  })

  it('9. loadRate 使用重新计算后的 avgLoad 和 peakLoad', () => {
    const hist = [h('2026-07-15T10:00:00', 800), h('2026-07-15T11:00:00', 900)]
    const hour10 = shTs('2026-07-15T10:30:00')
    const realtime = [rt(hour10, 1200)]
    const result = calculateDisplayStats(hist, realtime, RANGE_START, RANGE_END, true)
    // 小时代表值：10:00 avg=1200, 11:00=900 → avgLoad=1050
    // peakLoad=1200 (瞬时值), loadRate=1050/1200
    expect(result.peakLoad).toBe(1200)
    expect(result.avgLoad).toBeCloseTo(1050, 1)
    expect(result.loadRate).toBeCloseTo(1050 / 1200, 4)
  })

  it('10. 空数据返回安全结果且不产生 NaN', () => {
    const result = calculateDisplayStats([], [], RANGE_START, RANGE_END, true)
    expect(result.peakLoad).toBe(0)
    expect(result.valleyLoad).toBe(0)
    expect(result.avgLoad).toBe(0)
    expect(result.loadRate).toBe(0)
    expect(result.stdDeviation).toBe(0)
    expect(result.dataPoints).toBe(0)
    expect(result.peakTime).toBeNull()
    expect(result.valleyTime).toBeNull()
    // 确保没有 NaN
    expect(Number.isNaN(result.avgLoad)).toBe(false)
    expect(Number.isNaN(result.loadRate)).toBe(false)
    expect(Number.isNaN(result.stdDeviation)).toBe(false)
  })

  it('11. 输入数组没有被修改', () => {
    const hist = [h('2026-07-15T10:00:00', 800)]
    const realtime = [rt(shTs('2026-07-15T10:30:00'), 900)]
    const histCopy = JSON.stringify(hist)
    const rtCopy = JSON.stringify(realtime)
    calculateDisplayStats(hist, realtime, RANGE_START, RANGE_END, true)
    expect(JSON.stringify(hist)).toBe(histCopy)
    expect(JSON.stringify(realtime)).toBe(rtCopy)
  })

  it('12. 示例数据验证：hist=(10:00=800, 11:00=900) + rt=(11:30=1000, 11:31=1200)', () => {
    const hist = [
      h('2026-07-15T10:00:00', 800),
      h('2026-07-15T11:00:00', 900),
    ]
    const hour11_30 = shTs('2026-07-15T11:30:00')
    const hour11_31 = shTs('2026-07-15T11:31:00')
    const realtime = [rt(hour11_30, 1000), rt(hour11_31, 1200)]
    const result = calculateDisplayStats(hist, realtime, RANGE_START, RANGE_END, true)
    // 10:00 小时代表值=800, 11:00 小时代表值=avg(1000,1200)=1100
    // avgLoad = (800+1100)/2 = 950
    // peakLoad = 1200 (瞬时值)
    // valleyLoad = 800
    // dataPoints = 2
    // loadRate = 950/1200
    expect(result.dataPoints).toBe(2)
    expect(result.avgLoad).toBeCloseTo(950, 1)
    expect(result.peakLoad).toBe(1200)
    expect(result.valleyLoad).toBe(800)
    expect(result.loadRate).toBeCloseTo(950 / 1200, 4)
  })

  it('13. Asia/Shanghai 小时分桶在日期边界正确', () => {
    // 2026-07-15 23:30 Shanghai = 23:30 in epoch ms
    const lateNight = shTs('2026-07-15T23:30:00')
    const earlyMorning = shTs('2026-07-16T00:30:00')
    const hist = [
      h('2026-07-15T23:00:00', 700),
      h('2026-07-16T00:00:00', 600),
    ]
    const realtime = [
      rt(lateNight, 750),     // 归入 23:00 桶
      rt(earlyMorning, 650),  // 归入 00:00 桶
    ]
    const extendedEnd = shTs('2026-07-16T01:00:00')
    const result = calculateDisplayStats(hist, realtime, RANGE_START, extendedEnd, true)
    // 应该正确分成不同的小时桶
    expect(result.dataPoints).toBeGreaterThanOrEqual(2)
    // 23:00桶的瞬时峰值应该是750
    expect(result.peakLoad).toBeGreaterThanOrEqual(750)
  })
})
