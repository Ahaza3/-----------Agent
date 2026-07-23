/**
 * 实时点状态管理测试 — 追加、去重、排序、溢出入、快照合并
 */
import { describe, it, expect, beforeEach } from 'vitest'
import useDashboardStore from '../stores/useDashboardStore'
import type { RealtimeLoadPoint } from '../types/load'

function makePoint(sequence: number, timestamp: number, loadMw = 800): RealtimeLoadPoint {
  return { timestamp, sequence, loadMw, temperature: 25, humidity: 60, source: 'MOCK' }
}

describe('RealtimeLoadPoint Store', () => {
  beforeEach(() => {
    useDashboardStore.getState().reset()
  })

  it('1. appendRealtimeLoad 追加点并按 sequence 排序', () => {
    const store = useDashboardStore.getState()

    store.appendRealtimeLoad(makePoint(1, 1000))
    store.appendRealtimeLoad(makePoint(2, 2000))
    store.appendRealtimeLoad(makePoint(3, 3000))

    const pts = useDashboardStore.getState().realtimeLoads
    expect(pts).toHaveLength(3)
    expect(pts[0].sequence).toBe(1)
    expect(pts[1].sequence).toBe(2)
    expect(pts[2].sequence).toBe(3)
  })

  it('2. 重复 sequence 被去重', () => {
    const store = useDashboardStore.getState()

    store.appendRealtimeLoad(makePoint(1, 1000))
    store.appendRealtimeLoad(makePoint(1, 1000)) // 重复
    store.appendRealtimeLoad(makePoint(2, 2000))

    expect(useDashboardStore.getState().realtimeLoads).toHaveLength(2)
  })

  it('3. 倒序消息按观测时间合并，不覆盖最新点', () => {
    const store = useDashboardStore.getState()

    store.appendRealtimeLoad(makePoint(5, 5000))
    store.appendRealtimeLoad(makePoint(3, 3000)) // seq 3 < 5，应拒绝

    const pts = useDashboardStore.getState().realtimeLoads
    expect(pts).toHaveLength(2)
    expect(pts.map((point) => point.sequence)).toEqual([3, 5])
  })

  it('4. 超过 3600 点正确截断', () => {
    const store = useDashboardStore.getState()

    for (let i = 1; i <= 3700; i++) {
      store.appendRealtimeLoad(makePoint(i, i * 1000))
    }

    const pts = useDashboardStore.getState().realtimeLoads
    expect(pts).toHaveLength(3600)
    expect(pts[0].sequence).toBe(101) // 最旧的 100 个被淘汰
    expect(pts[pts.length - 1].sequence).toBe(3700)
  })

  it('5. 当前负荷 liveLoad 等于 realtimeLoads 最后一个点', () => {
    const store = useDashboardStore.getState()

    store.appendRealtimeLoad(makePoint(1, 1000, 800))
    store.appendRealtimeLoad(makePoint(2, 2000, 850))

    const state = useDashboardStore.getState()
    expect(state.liveLoad?.loadMw).toBe(850)
    expect(state.realtimeLoads[state.realtimeLoads.length - 1].loadMw).toBe(850)
  })

  it('6. mergeRealtimeLoads 合并快照不丢点，去重', () => {
    const store = useDashboardStore.getState()

    // 已有 3 个实时点
    store.appendRealtimeLoad(makePoint(1, 1000))
    store.appendRealtimeLoad(makePoint(2, 2000))
    store.appendRealtimeLoad(makePoint(3, 3000))

    // 快照拉取到 seq 2-5
    const snapshot = [
      makePoint(2, 2000),  // 重复
      makePoint(3, 3000),  // 重复
      makePoint(4, 4000),  // 新
      makePoint(5, 5000),  // 新
    ]
    store.mergeRealtimeLoads(snapshot)

    const pts = useDashboardStore.getState().realtimeLoads
    expect(pts).toHaveLength(5)
    expect(pts.map((p) => p.sequence)).toEqual([1, 2, 3, 4, 5])
  })

  it('7. 重连快照不会造成重复数据', () => {
    const store = useDashboardStore.getState()

    // 模拟首次连接：先追加了一些 WS 消息
    store.appendRealtimeLoad(makePoint(1, 1000))
    store.appendRealtimeLoad(makePoint(2, 2000))
    store.appendRealtimeLoad(makePoint(3, 3000))

    // 拉取快照（包含已经在 WS 中收到的点）
    const snapshot = [
      makePoint(1, 1000),
      makePoint(2, 2000),
      makePoint(3, 3000),
      makePoint(4, 4000),
    ]
    store.mergeRealtimeLoads(snapshot)

    // 再次合并同一快照（模拟重连）
    store.mergeRealtimeLoads(snapshot)

    const pts = useDashboardStore.getState().realtimeLoads
    // 不应重复
    const seqs = pts.map((p) => p.sequence)
    expect(seqs).toEqual([1, 2, 3, 4])
    expect(pts).toHaveLength(4)
  })

  it('8. 自定义历史区间不拼接当前实时点 — 由 isLive 标志在 Dashboard 层面处理', () => {
    // 这个逻辑在 Dashboard 的 loadChartOption 中验证
    // store 本身不区分历史/实时模式，所有点都累积
    const store = useDashboardStore.getState()
    store.appendRealtimeLoad(makePoint(1, 1000, 800))

    // store 保持所有点，但图表组件根据 isLive 决定是否拼接
    expect(useDashboardStore.getState().realtimeLoads).toHaveLength(1)
  })

  it('9. WebSocket 连接状态可切换', () => {
    const store = useDashboardStore.getState()
    expect(store.wsConnected).toBe(false)

    store.setWsConnected(true)
    expect(useDashboardStore.getState().wsConnected).toBe(true)

    store.setWsConnected(false)
    expect(useDashboardStore.getState().wsConnected).toBe(false)
  })

  it('10. lastRealtimeAt 随 append 更新', () => {
    const store = useDashboardStore.getState()
    const before = Date.now()
    store.appendRealtimeLoad(makePoint(1, before))
    const after = useDashboardStore.getState().lastRealtimeAt
    expect(after).toBeGreaterThanOrEqual(before)
  })

  it('11. 后端重启导致 sequence 归零时仍接收时间更新的点', () => {
    const store = useDashboardStore.getState()
    store.appendRealtimeLoad(makePoint(200, 1000, 800))
    store.appendRealtimeLoad(makePoint(1, 2000, 801))

    const points = useDashboardStore.getState().realtimeLoads
    expect(points.map((point) => point.sequence)).toEqual([200, 1])
    expect(useDashboardStore.getState().liveLoad?.loadMw).toBe(801)
  })

  it('12. 合并跨重启快照时保留 sequence 相同但时间不同的点', () => {
    const store = useDashboardStore.getState()
    store.mergeRealtimeLoads([
      makePoint(1, 1000, 800),
      makePoint(1, 2000, 801),
      makePoint(1, 2000, 801),
    ])

    const points = useDashboardStore.getState().realtimeLoads
    expect(points).toHaveLength(2)
    expect(points.map((point) => point.timestamp)).toEqual([1000, 2000])
  })
})
