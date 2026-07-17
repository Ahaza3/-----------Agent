/**
 * WebSocket 持久连接 — 挂在 AuthGuard 内部，认证后才挂载
 * 渲染 <Outlet /> 让子路由正常工作
 */
import { Outlet } from 'react-router-dom'
import { useWebSocket } from './useWebSocket'

const WebSocketProvider = () => {
  useWebSocket()
  return <Outlet />
}

export default WebSocketProvider
