/**
 * 仪表盘全局状态 — Zustand store
 */
import { create } from 'zustand'
import type { LoadData, RealtimeLoadPoint } from '../types/load'
import type { PredictionResult } from '../types/prediction'
import type { AlertEvent } from '../types/alert'
import type { LoadStats } from '../types/load'
import type { ForecastResponse } from '../types/prediction'

const MAX_HIST_POINTS = 1000
const MAX_REALTIME_POINTS = 3600

interface DashboardState {
  /** 图表数据（小时级，来自 REST + MockDataFeeder 小时插入） */
  loadData: LoadData[]
  /** 实时当前值 — 始终等于 realtimeLoads 最后一个点，不作为独立状态 */
  liveLoad: LoadData | null

  /** 实时秒级数据点（WebSocket 累积 + 快照合并） */
  realtimeLoads: RealtimeLoadPoint[]

  predictions: PredictionResult[]
  forecast: ForecastResponse | null
  alerts: AlertEvent[]
  stats: LoadStats | null
  loading: boolean

  /** WebSocket 连接状态 */
  wsConnected: boolean
  /** 最后收到实时数据的时间（epoch ms） */
  lastRealtimeAt: number

  setLoadData: (data: LoadData[]) => void
  /** 追加小时级数据到图表（MockDataFeeder 插入后推送） */
  appendLoadData: (data: LoadData) => void
  /** 设置实时当前值（兼容旧接口 — 不再使用） */
  setLiveLoad: (data: LoadData) => void
  setForecast: (data: ForecastResponse) => void
  setPredictions: (data: PredictionResult[]) => void
  setAlerts: (data: AlertEvent[]) => void
  appendAlert: (alert: AlertEvent) => void
  acknowledgeAlert: (alertId: number) => void
  setStats: (stats: LoadStats) => void
  setLoading: (loading: boolean) => void

  /** 设置 WebSocket 连接状态 */
  setWsConnected: (connected: boolean) => void

  /** 追加单个实时点（WebSocket 推送） — 去重、排序、溢出淘汰 */
  appendRealtimeLoad: (point: RealtimeLoadPoint) => void

  /** 合并快照数据（重连 / 首次加载拉取） — 去重、排序、溢出淘汰 */
  mergeRealtimeLoads: (points: RealtimeLoadPoint[]) => void

  reset: () => void
}

const initialState = {
  loadData: [] as LoadData[],
  liveLoad: null as LoadData | null,
  realtimeLoads: [] as RealtimeLoadPoint[],
  predictions: [] as PredictionResult[],
  forecast: null as ForecastResponse | null,
  alerts: [] as AlertEvent[],
  stats: null as LoadStats | null,
  loading: false,
  wsConnected: false,
  lastRealtimeAt: 0,
}

const useDashboardStore = create<DashboardState>((set) => ({
  ...initialState,

  setLoadData: (data) =>
    set((state) => {
      if (data.length === 0) return { loadData: data }
      const lastRest = data[data.length - 1]
      const restTimes = new Set(data.map((d) => d.time))
      const wsTail = state.loadData.filter(
        (d) => !restTimes.has(d.time) && new Date(d.time) > new Date(lastRest.time),
      )
      const merged = [...data, ...wsTail]
      return { loadData: merged.length > MAX_HIST_POINTS ? merged.slice(-MAX_HIST_POINTS) : merged }
    }),

  appendLoadData: (data) =>
    set((state) => {
      const last = state.loadData[state.loadData.length - 1]
      if (last && last.time === data.time) return state
      if (last && new Date(data.time) <= new Date(last.time)) return state
      return { loadData: [...state.loadData.slice(1 - MAX_HIST_POINTS), data] }
    }),

  setLiveLoad: (data) => set({ liveLoad: data }),

  setForecast: (data) => set({ forecast: data }),
  setPredictions: (data) => set({ predictions: data }),
  setAlerts: (data) => set({ alerts: data }),

  appendAlert: (alert) =>
    set((state) => {
      if (state.alerts.some((a) => a.id === alert.id)) return state
      return { alerts: [alert, ...state.alerts] }
    }),

  acknowledgeAlert: (alertId) =>
    set((state) => ({
      alerts: state.alerts.map((a) =>
        a.id === alertId ? { ...a, isRead: 1 } : a,
      ),
    })),

  setStats: (stats) => set({ stats }),
  setLoading: (loading) => set({ loading }),
  setWsConnected: (connected) => set({ wsConnected: connected }),

  /* ─── 实时点操作 ─── */

  appendRealtimeLoad: (point) =>
    set((state) => {
      // sequence 会在后端重启后归零，时间戳才是跨会话的稳定顺序依据。
      const lastTimestamp = state.realtimeLoads.length > 0
        ? state.realtimeLoads[state.realtimeLoads.length - 1].timestamp
        : -1
      if (point.timestamp <= lastTimestamp) return state

      const next = [...state.realtimeLoads, point]
      if (next.length > MAX_REALTIME_POINTS) {
        next.splice(0, next.length - MAX_REALTIME_POINTS)
      }

      // liveLoad 同步更新为最后一个实时点
      const latest = next[next.length - 1]
      const liveLoad = latest ? realtimePointToLiveLoad(latest) : state.liveLoad

      return {
        realtimeLoads: next,
        liveLoad,
        lastRealtimeAt: Date.now(),
      }
    }),

  mergeRealtimeLoads: (points) =>
    set((state) => {
      if (points.length === 0) return state

      // 后端重启后 sequence 会重复，使用采样时间去重并按时间排序。
      const existingTimestamps = new Set(state.realtimeLoads.map((p) => p.timestamp))
      const merged = [...state.realtimeLoads]
      for (const p of points) {
        if (!existingTimestamps.has(p.timestamp)) {
          merged.push(p)
          existingTimestamps.add(p.timestamp)
        }
      }

      merged.sort((a, b) => a.timestamp - b.timestamp || a.sequence - b.sequence)

      // 溢出淘汰
      if (merged.length > MAX_REALTIME_POINTS) {
        merged.splice(0, merged.length - MAX_REALTIME_POINTS)
      }

      const latest = merged[merged.length - 1]
      const liveLoad = latest ? realtimePointToLiveLoad(latest) : state.liveLoad

      return {
        realtimeLoads: merged,
        liveLoad,
        lastRealtimeAt: Date.now(),
      }
    }),

  reset: () => set(initialState),
}))

/** 将 RealtimeLoadPoint 转为兼容旧 UI 的 LoadData 格式 */
function realtimePointToLiveLoad(p: RealtimeLoadPoint): LoadData {
  return {
    id: 0,
    time: new Date(p.timestamp).toISOString(),
    loadMw: p.loadMw,
    temperature: p.temperature ?? 0,
    humidity: p.humidity ?? 0,
    isHoliday: 0,
    hour: new Date(p.timestamp).getHours(),
    dayOfWeek: new Date(p.timestamp).getDay(),
    month: new Date(p.timestamp).getMonth() + 1,
    createdAt: new Date().toISOString(),
  }
}

export default useDashboardStore
