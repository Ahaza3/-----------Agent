import { Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider, App as AntApp } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import themeToken from './theme/tokens'

// 页面（Day 4 占位，后续 Sprint 实现）
import Dashboard from './pages/Dashboard'
import AlertCenter from './pages/AlertCenter'
import AgentChat from './pages/AgentChat'
import DataQuery from './pages/DataQuery'
import Admin from './pages/Admin'

function App() {
  return (
    <ConfigProvider theme={themeToken} locale={zhCN}>
      <AntApp>
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/alerts" element={<AlertCenter />} />
          <Route path="/agent" element={<AgentChat />} />
          <Route path="/data" element={<DataQuery />} />
          <Route path="/admin" element={<Admin />} />
        </Routes>
      </AntApp>
    </ConfigProvider>
  )
}

export default App
