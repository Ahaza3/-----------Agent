/**
 * 快照合并高级场景测试
 */
import { describe, it, expect, beforeEach } from 'vitest'
import useDashboardStore from '../stores/useDashboardStore'
import type { RealtimeLoadPoint } from '../types/load'

function makePoint(sequence: number, timestamp: number, loadMw = 800): RealtimeLoadPoint {
  return { timestamp, sequence, loadMw, temperature: 25, humidity: 60, source: 'MOCK' }
}

describe('Snapshot merge scenarios', () => {
  beforeEach(() => {
    useDashboardStore.getState().reset()
  })

  it('快照与连接期间 WS 消息合并 — 不丢点', () => {
    const store = useDashboardStore.getState()

    // 连接后立即收到 1 条 WS 消息
    store.appendRealtimeLoad(makePoint(1, 1000))

    // 快照拉取返回 seq 1-3
    store.mergeRealtimeLoads([
      makePoint(1, 1000),
      makePoint(2, 2000),
      makePoint(3, 3000),
    ])

    // 快照结束后又收到 WS 消息
    store.appendRealtimeLoad(makePoint(4, 4000))
    store.appendRealtimeLoad(makePoint(5, 5000))

    const pts = useDashboardStore.getState().realtimeLoads
    expect(pts.map((p) => p.sequence)).toEqual([1, 2, 3, 4, 5])
  })

  it('乱序快照合并后仍保持有序', () => {
    const store = useDashboardStore.getState()

    // 快照以非严格顺序返回
    store.mergeRealtimeLoads([
      makePoint(5, 5000),
      makePoint(2, 2000),
      makePoint(8, 8000),
      makePoint(1, 1000),
    ])

    const pts = useDashboardStore.getState().realtimeLoads
    const seqs = pts.map((p) => p.sequence)

    // 验证升序
    for (let i = 1; i < seqs.length; i++) {
      expect(seqs[i]).toBeGreaterThan(seqs[i - 1])
    }
    expect(seqs).toEqual([1, 2, 5, 8])
  })

  it('空快照不改变现有数据', () => {
    const store = useDashboardStore.getState()

    store.appendRealtimeLoad(makePoint(1, 1000))
    store.appendRealtimeLoad(makePoint(2, 2000))

    store.mergeRealtimeLoads([])

    expect(useDashboardStore.getState().realtimeLoads).toHaveLength(2)
  })

  it('3600 点边界 — 合并后不超过上限', () => {
    const store = useDashboardStore.getState()

    // 填满
    for (let i = 1; i <= 3598; i++) {
      store.appendRealtimeLoad(makePoint(i, i * 1000))
    }

    // 合并 10 个新点
    const snapshot = Array.from({ length: 10 }, (_, i) =>
      makePoint(3598 + i, (3598 + i) * 1000)
    )
    store.mergeRealtimeLoads(snapshot)

    const pts = useDashboardStore.getState().realtimeLoads
    expect(pts.length).toBeLessThanOrEqual(3600)
  })
})
