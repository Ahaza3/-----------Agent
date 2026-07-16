/**
 * 展示统计计算（纯函数）
 *
 * 按 Asia/Shanghai 整点小时分桶，每小时一个等权样本计算平均值，
 * 峰值/谷值保留实时瞬时极值。不修改传入数组。
 */
import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import type { LoadData, LoadStats, RealtimeLoadPoint } from '../../types/load'

dayjs.extend(utc)

/** Asia/Shanghai = UTC+8 */
const SHANGHAI_OFFSET_MINUTES = 480

/**
 * 将 epoch ms 时间戳归入 Asia/Shanghai 整点小时桶键（epoch ms of that hour start）。
 */
function shanghaiHourBucket(epochMs: number): number {
  return dayjs(epochMs).utcOffset(SHANGHAI_OFFSET_MINUTES).startOf('hour').valueOf()
}

/**
 * 从 ISO 时间字符串（后端 Asia/Shanghai LocalDateTime，无时区标记）提取小时桶键。
 * 附加 +08:00 让 dayjs 正确解析。
 */
function shanghaiHourBucketFromString(timeStr: string): number {
  const d = dayjs(timeStr + '+08:00')
  return d.startOf('hour').valueOf()
}

export function calculateDisplayStats(
  historical: readonly LoadData[],
  realtime: readonly RealtimeLoadPoint[],
  rangeStart: number,   // epoch ms
  rangeEnd: number,     // epoch ms
  includeRealtime: boolean,
): LoadStats {
  // ── 1. 过滤 ──────────────────────────────────
  const histFiltered = historical.filter((d) => {
    const t = shanghaiHourBucketFromString(d.time).valueOf()
    return t >= rangeStart && t <= rangeEnd
  })

  const rtFiltered = includeRealtime
    ? realtime.filter((p) => p.timestamp >= rangeStart && p.timestamp <= rangeEnd)
    : []

  // ── 2. 小时分桶：每小时取一个 historical 值 ──
  // historical 同一小时只保留第一个（或最后一个，这里取最后一个）
  const histByHour = new Map<number, LoadData>()
  for (const d of histFiltered) {
    const bucket = shanghaiHourBucketFromString(d.time)
    // 同一小时保留最后一个（最新的）
    histByHour.set(bucket, d)
  }

  // ── 3. 实时点按小时分组 ──
  type RtBucket = { sum: number; count: number; points: RealtimeLoadPoint[] }
  const rtByHour = new Map<number, RtBucket>()
  for (const p of rtFiltered) {
    const bucket = shanghaiHourBucket(p.timestamp)
    let b = rtByHour.get(bucket)
    if (!b) {
      b = { sum: 0, count: 0, points: [] }
      rtByHour.set(bucket, b)
    }
    b.sum += p.loadMw
    b.count += 1
    b.points.push(p)
  }

  // ── 4. 构建小时代表值 ──
  // 所有出现的小时桶（合并 historical + realtime）
  const allBuckets = new Set([...histByHour.keys(), ...rtByHour.keys()])

  // 每小时代表值（用于计算平均值和标准差）
  const hourlyValues: number[] = []

  // 用于计算峰谷值的候选点（负荷值 + 时间 epoch ms）
  type Candidate = { value: number; timeMs: number }
  const candidates: Candidate[] = []

  for (const bucket of allBuckets) {
    const hist = histByHour.get(bucket)
    const rt = rtByHour.get(bucket)

    if (rt) {
      // 有实时数据：小时代表值 = 该小时实时点平均值
      const avg = rt.sum / rt.count
      hourlyValues.push(avg)

      // 峰谷候选：使用该小时全部实时原始点
      for (const p of rt.points) {
        candidates.push({ value: p.loadMw, timeMs: p.timestamp })
      }
      // 排除该小时 historical 点
    } else if (hist) {
      // 仅有历史数据
      hourlyValues.push(hist.loadMw)
      candidates.push({ value: hist.loadMw, timeMs: shanghaiHourBucketFromString(hist.time) })
    }
  }

  // ── 5. 空数据 ──
  if (candidates.length === 0) {
    return {
      peakLoad: 0,
      peakTime: null,
      valleyLoad: 0,
      valleyTime: null,
      avgLoad: 0,
      loadRate: 0,
      stdDeviation: 0,
      dataPoints: 0,
    }
  }

  // ── 6. 峰谷值 ──
  let peak = candidates[0]
  let valley = candidates[0]
  for (const c of candidates) {
    if (c.value > peak.value) peak = c
    if (c.value < valley.value) valley = c
  }

  // ── 7. 平均负荷 ──
  const sum = hourlyValues.reduce((a, b) => a + b, 0)
  const avgLoad = sum / hourlyValues.length

  // ── 8. 标准差 ──
  const variance =
    hourlyValues.reduce((s, v) => s + (v - avgLoad) ** 2, 0) / hourlyValues.length
  const stdDeviation = Math.sqrt(variance)

  // ── 9. 负荷率 ──
  const loadRate = peak.value > 0 ? avgLoad / peak.value : 0

  return {
    peakLoad: Math.round(peak.value * 100) / 100,
    peakTime: new Date(peak.timeMs).toISOString(),
    valleyLoad: Math.round(valley.value * 100) / 100,
    valleyTime: new Date(valley.timeMs).toISOString(),
    avgLoad: Math.round(avgLoad * 100) / 100,
    loadRate: Math.round(loadRate * 10000) / 10000,
    stdDeviation: Math.round(stdDeviation * 100) / 100,
    dataPoints: hourlyValues.length,
  }
}
