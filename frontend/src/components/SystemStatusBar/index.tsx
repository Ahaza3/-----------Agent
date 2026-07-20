import { useEffect } from 'react'
import { Alert, Button } from 'antd'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import useSystemStatusStore from '../../stores/useSystemStatusStore'
import useDashboardStore from '../../stores/useDashboardStore'

const SystemStatusBar = () => {
  const setWsState = useSystemStatusStore((s) => s.setWsState)
  const showDisconnectedBanner = useSystemStatusStore((s) => s.showDisconnectedBanner)
  const showRecoveredBanner = useSystemStatusStore((s) => s.showRecoveredBanner)
  const hideDisconnected = useSystemStatusStore((s) => s.hideDisconnected)
  const hideRecovered = useSystemStatusStore((s) => s.hideRecovered)

  const wsConnected = useDashboardStore((s) => s.wsConnected)
  const dashboardLastRealtimeAt = useDashboardStore((s) => s.lastRealtimeAt)

  useEffect(() => {
    setWsState(wsConnected ? 'CONNECTED' : 'DISCONNECTED')
  }, [wsConnected, setWsState])

  useEffect(() => {
    if (dashboardLastRealtimeAt > 0) {
      useSystemStatusStore.getState().setLastRealtimeAt(dashboardLastRealtimeAt)
    }
  }, [dashboardLastRealtimeAt])

  useEffect(() => {
    if (!showRecoveredBanner) return
    const timer = window.setTimeout(() => hideRecovered(), 5000)
    return () => window.clearTimeout(timer)
  }, [showRecoveredBanner, hideRecovered])

  const handleReconnect = () => {
    setWsState('RECONNECTING')
    window.dispatchEvent(new CustomEvent('ws:reconnect'))
  }

  if (!showDisconnectedBanner && !showRecoveredBanner) return null

  return (
    <div className="system-status-bar">
      {showDisconnectedBanner && (
        <Alert
          type="error"
          showIcon
          icon={<CloseCircleOutlined />}
          message="实时连接中断，当前曲线可能不是最新状态"
          action={
            <Button size="small" danger icon={<ReloadOutlined />} onClick={handleReconnect}>
              重新连接
            </Button>
          }
          banner
          closable
          onClose={hideDisconnected}
          className="system-status-bar__banner system-status-bar__banner--error"
        />
      )}

      {showRecoveredBanner && (
        <Alert
          type="success"
          showIcon
          icon={<CheckCircleOutlined />}
          message="实时连接已恢复"
          banner
          closable
          onClose={hideRecovered}
          className="system-status-bar__banner system-status-bar__banner--success"
        />
      )}

      <style>{`
        .system-status-bar__banner {
          border-radius: 0 !important;
          border-left: none !important;
          border-right: none !important;
        }
        .system-status-bar__banner--error {
          background: rgba(216, 92, 92, 0.14) !important;
          border-color: #D85C5C !important;
        }
        .system-status-bar__banner--success {
          background: rgba(95, 167, 119, 0.12) !important;
          border-color: #5FA777 !important;
        }
      `}</style>
    </div>
  )
}

export default SystemStatusBar
