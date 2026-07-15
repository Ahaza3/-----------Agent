/**
 * 仪表盘全局状态 — Zustand store
 */
import { create } from 'zustand'
import type { LoadData } from '../types/load'
import type { PredictionResult } from '../types/prediction'
import type { AlertEvent } from '../types/alert'
import type { LoadStats } from '../types/load'
import type { ForecastResponse } from '../types/prediction'

const MAX_POINTS = 1000

interface DashboardState {
  /** 图表数据（小时级，来自 REST + MockDataFeeder 小时插入） */
  loadData: LoadData[]
  /** 实时当前值（秒级 WebSocket，仅用于仪表读数，不混入图表） */
  liveLoad: LoadData | null

  predictions: PredictionResult[]
  forecast: ForecastResponse | null
  alerts: AlertEvent[]
  stats: LoadStats | null
  loading: boolean

  setLoadData: (data: LoadData[]) => void
  /** 追加小时级数据到图表（MockDataFeeder 插入后推送） */
  appendLoadData: (data: LoadData) => void
  /** 设置实时当前值（LoadScheduler 推送） */
  setLiveLoad: (data: LoadData) => void
  setForecast: (data: ForecastResponse) => void
  setPredictions: (data: PredictionResult[]) => void
  setAlerts: (data: AlertEvent[]) => void
  appendAlert: (alert: AlertEvent) => void
  acknowledgeAlert: (alertId: number) => void
  setStats: (stats: LoadStats) => void
  setLoading: (loading: boolean) => void
  reset: () => void
}

const initialState = {
  loadData: [] as LoadData[],
  liveLoad: null as LoadData | null,
  predictions: [] as PredictionResult[],
  forecast: null as ForecastResponse | null,
  alerts: [] as AlertEvent[],
  stats: null as LoadStats | null,
  loading: false,
}

const useDashboardStore = create<DashboardState>((set) => ({
  ...initialState,

  setLoadData: (data) =>
    set((state) => {
      if (data.length === 0) return { loadData: data }
      const lastRest = data[data.length - 1]
      const restTimes = new Set(data.map((d) => d.time))
      // 保留 REST 请求期间到达的 WS 数据，但排除 REST 已包含的时间
      const wsTail = state.loadData.filter(
        (d) => !restTimes.has(d.time) && new Date(d.time) > new Date(lastRest.time),
      )
      const merged = [...data, ...wsTail]
      // 截断
      return { loadData: merged.length > MAX_POINTS ? merged.slice(-MAX_POINTS) : merged }
    }),

  appendLoadData: (data) =>
    set((state) => {
      const last = state.loadData[state.loadData.length - 1]
      if (last && last.time === data.time) return state
      if (last && new Date(data.time) <= new Date(last.time)) return state
      return { loadData: [...state.loadData.slice(1 - MAX_POINTS), data] }
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
  reset: () => set(initialState),
}))

export default useDashboardStore
