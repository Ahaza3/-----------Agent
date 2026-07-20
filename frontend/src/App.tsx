import { Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider, App as AntApp } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import themeToken from './theme/tokens'
import AuthGuard from './components/AuthGuard'
import RoleGuard from './components/RoleGuard'
import MainLayout from './layouts/MainLayout'
import WebSocketProvider from './hooks/WebSocketProvider'
import useAuthStore from './stores/useAuthStore'
import { getDefaultRoute } from './config/navigation'
import type { Role } from './config/roles'

import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import AlertCenter from './pages/AlertCenter'
import AgentChat from './pages/AgentChat'
import DataQuery from './pages/DataQuery'
import Admin from './pages/Admin'
import Forbidden from './pages/Forbidden'
import NotFound from './pages/NotFound'

function App() {
  return (
    <ConfigProvider theme={themeToken} locale={zhCN}>
      <AntApp>
        <Routes>
          {/* 公开路由 */}
          <Route path="/login" element={<Login />} />
          <Route path="/403" element={<Forbidden />} />
          <Route path="/404" element={<NotFound />} />

          {/* 认证路由 — 需要登录但具体角色 */}
          <Route element={<AuthGuard />}>
            {/* WebSocket 在认证之后才挂载 */}
            <Route element={<WebSocketProvider />}>
              <Route element={<MainLayout />}>
                {/* 根路径按角色路由到默认首页 */}
                <Route path="/" element={<RoleDefaultRedirect />} />

                {/* 调度员 & 操作员可见 */}
                <Route element={<RoleGuard allowedRoles={['DISPATCHER', 'OPERATOR']} />}>
                  <Route path="/dashboard" element={<Dashboard />} />
                  <Route path="/alerts" element={<AlertCenter />} />
                </Route>

                {/* 全部角色可见 */}
                <Route element={<RoleGuard allowedRoles={['DISPATCHER', 'OPERATOR', 'SYSTEM_ADMIN']} />}>
                  <Route path="/agent" element={<AgentChat />} />
                  <Route path="/data" element={<DataQuery />} />
                </Route>

                {/* 操作员 & 系统管理员可见 */}
                <Route element={<RoleGuard allowedRoles={['OPERATOR', 'SYSTEM_ADMIN']} />}>
                  <Route path="/admin" element={<Admin />} />
                </Route>
              </Route>
            </Route>
          </Route>

          {/* 404 兜底 */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </AntApp>
    </ConfigProvider>
  )
}

/** 登录后按角色跳转默认首页 */
function RoleDefaultRedirect() {
  const role = useAuthStore((s) => s.user?.role) as Role | undefined
  return <Navigate to={getDefaultRoute(role)} replace />
}

export default App
