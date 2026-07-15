/**
 * 仪表盘全局状态 — Zustand store
 * 管理实时负荷、预测结果、告警事件、概览统计
 */
import { create } from 'zustand'
import type { LoadData } from '../types/load'
import type { PredictionResult } from '../types/prediction'
import type { AlertEvent } from '../types/alert'
import type { LoadStats } from '../types/load'
import type { ForecastResponse } from '../types/prediction'

interface DashboardState {
  // ---- 数据 ----
  /** 实时负荷数据 (最新 N 条) */
  loadData: LoadData[]
  /** 预测结果 */
  predictions: PredictionResult[]
  /** 预测原始数据（来自 /predict/forecast） */
  forecast: ForecastResponse | null
  /** 告警事件 */
  alerts: AlertEvent[]
  /** 仪表盘统计概览 */
  stats: LoadStats | null
  /** 数据加载中 */
  loading: boolean

  // ---- 动作 ----
  /** 设置负荷数据 */
  setLoadData: (data: LoadData[]) => void
  /** 追加负荷数据（WebSocket 实时推送） */
  appendLoadData: (data: LoadData) => void
  /** 设置预测原始数据 */
  setForecast: (data: ForecastResponse) => void
  /** 设置预测结果 */
  setPredictions: (data: PredictionResult[]) => void
  /** 设置告警事件 */
  setAlerts: (data: AlertEvent[]) => void
  /** 追加新告警（WebSocket 实时推送） */
  appendAlert: (alert: AlertEvent) => void
  /** 确认告警 */
  acknowledgeAlert: (alertId: number) => void
  /** 设置统计概览 */
  setStats: (stats: LoadStats) => void
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void
  /** 重置全部数据 */
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

  setLoadData: (data) => set({ loadData: data }),

  appendLoadData: (data) =>
    set((state) => ({
      loadData: [...state.loadData.slice(-999), data], // 5s→约 83 分钟
    })),

  setForecast: (data) => set({ forecast: data }),

  setPredictions: (data) => set({ predictions: data }),

  setAlerts: (data) => set({ alerts: data }),

  appendAlert: (alert) =>
    set((state) => ({
      alerts: [alert, ...state.alerts], // 新告警置顶
    })),

  acknowledgeAlert: (alertId) =>
    set((state) => ({
      alerts: state.alerts.map((a) =>
        a.id === alertId ? { ...a, acknowledged: true } : a,
      ),
    })),

  setStats: (stats) => set({ stats }),

  setLoading: (loading) => set({ loading }),

  reset: () => set(initialState),
}))

export default useDashboardStore
