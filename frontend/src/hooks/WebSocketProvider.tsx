/**
 * WebSocket 持久连接 — 挂载在 App 层，跨页面保持连接
 * 渲染 null（不产生 DOM），仅管理连接生命周期
 */
import { useWebSocket } from './useWebSocket'

const WebSocketProvider = () => {
  useWebSocket()
  return null
}

export default WebSocketProvider
