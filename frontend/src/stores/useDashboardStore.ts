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
  loadData: LoadData[]
  predictions: PredictionResult[]
  forecast: ForecastResponse | null
  alerts: AlertEvent[]
  stats: LoadStats | null
  loading: boolean

  setLoadData: (data: LoadData[]) => void
  /** 追加 + 去重：time 相同则跳过 */
  appendLoadData: (data: LoadData) => void
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
      // 智能合并：保留 HTTP 请求期间到达的 WebSocket 实时数据
      const lastWs = state.loadData[state.loadData.length - 1]
      if (!lastWs || data.length === 0) return { loadData: data }

      const lastRest = data[data.length - 1]
      // REST 数据终止时间之后的 WS 数据全部保留
      const wsTail = state.loadData.filter(
        (d) => new Date(d.time) > new Date(lastRest.time),
      )
      return { loadData: [...data, ...wsTail] }
    }),

  appendLoadData: (data) =>
    set((state) => {
      const last = state.loadData[state.loadData.length - 1]
      // 去重：同一时间不重复追加
      if (last && last.time === data.time) return state
      // 时间不回退
      if (last && new Date(data.time) <= new Date(last.time)) return state
      return { loadData: [...state.loadData.slice(1 - MAX_POINTS), data] }
    }),

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
