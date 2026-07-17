/**
 * 系统全局状态 — WebSocket 连接、系统健康、数据时效
 */
import { create } from 'zustand'

export type WsConnectionState = 'CONNECTING' | 'CONNECTED' | 'RECONNECTING' | 'DISCONNECTED'

interface SystemStatusState {
  /** WebSocket 连接状态 */
  wsState: WsConnectionState
  /** 最后实时数据到达时间 (epoch ms) */
  lastRealtimeAt: number
  /** 最后预测时间 (ISO string) */
  lastPredictionAt: string | null
  /** 数据来源 */
  dataSource: string
  /** 后端健康数据缓存 */
  health: {
    status: string | null
    mysql: string | null
    flask: string | null
    llm: { configured: boolean; model: string } | null
    uptimeSeconds: number
  } | null
  /** 健康检查加载中 */
  healthLoading: boolean
  /** 健康检查错误 */
  healthError: string | null
  /** 重连横幅是否可见（断开时自动显示） */
  showDisconnectedBanner: boolean
  /** 恢复横幅是否可见（重连后显示，5s 后自动关闭） */
  showRecoveredBanner: boolean

  setWsState: (state: WsConnectionState) => void
  setLastRealtimeAt: (ts: number) => void
  setLastPredictionAt: (ts: string | null) => void
  setDataSource: (source: string) => void
  setHealth: (health: SystemStatusState['health']) => void
  setHealthLoading: (loading: boolean) => void
  setHealthError: (error: string | null) => void
  showDisconnected: () => void
  hideDisconnected: () => void
  showRecovered: () => void
  hideRecovered: () => void
}

const useSystemStatusStore = create<SystemStatusState>((set) => ({
  wsState: 'DISCONNECTED',
  lastRealtimeAt: 0,
  lastPredictionAt: null,
  dataSource: '--',
  health: null,
  healthLoading: false,
  healthError: null,
  showDisconnectedBanner: false,
  showRecoveredBanner: false,

  setWsState: (wsState) =>
    set((prev) => {
      const updates: Partial<SystemStatusState> = { wsState }
      if (wsState === 'CONNECTED') {
        updates.showDisconnectedBanner = false
        if (prev.wsState === 'DISCONNECTED' || prev.wsState === 'RECONNECTING') {
          updates.showRecoveredBanner = true
        }
      }
      if (wsState === 'DISCONNECTED') {
        updates.showDisconnectedBanner = true
        updates.showRecoveredBanner = false
      }
      if (wsState === 'RECONNECTING') {
        updates.showDisconnectedBanner = true
        updates.showRecoveredBanner = false
      }
      return updates as SystemStatusState
    }),

  setLastRealtimeAt: (ts) => set({ lastRealtimeAt: ts }),
  setLastPredictionAt: (ts) => set({ lastPredictionAt: ts }),
  setDataSource: (source) => set({ dataSource: source }),
  setHealth: (health) => set({ health }),
  setHealthLoading: (loading) => set({ healthLoading: loading }),
  setHealthError: (error) => set({ healthError: error }),
  showDisconnected: () => set({ showDisconnectedBanner: true }),
  hideDisconnected: () => set({ showDisconnectedBanner: false }),
  showRecovered: () => set({ showRecoveredBanner: true }),
  hideRecovered: () => set({ showRecoveredBanner: false }),
}))

export default useSystemStatusStore
