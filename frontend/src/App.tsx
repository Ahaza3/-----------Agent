import { Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider, App as AntApp } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import themeToken from './theme/tokens'
import MainLayout from './layouts/MainLayout'
import WebSocketProvider from './hooks/WebSocketProvider'

import Dashboard from './pages/Dashboard'
import AlertCenter from './pages/AlertCenter'
import AgentChat from './pages/AgentChat'
import DataQuery from './pages/DataQuery'
import Admin from './pages/Admin'

function App() {
  return (
    <ConfigProvider theme={themeToken} locale={zhCN}>
      <AntApp>
        <WebSocketProvider />
        <Routes>
          <Route element={<MainLayout />}>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/alerts" element={<AlertCenter />} />
            <Route path="/agent" element={<AgentChat />} />
            <Route path="/data" element={<DataQuery />} />
            <Route path="/admin" element={<Admin />} />
          </Route>
        </Routes>
      </AntApp>
    </ConfigProvider>
  )
}

export default App
