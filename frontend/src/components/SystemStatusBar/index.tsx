/**
 * 全局运行状态条 — 紧凑状态条 + 断连持久横幅
 * 不依赖颜色传递信息，同时使用图标和文字
 */
import { useEffect, useRef, useCallback } from 'react'
import { Alert, Button, Space, Tag, Tooltip } from 'antd'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  ReloadOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import useSystemStatusStore from '../../stores/useSystemStatusStore'
import useDashboardStore from '../../stores/useDashboardStore'
import api from '../../services/api'

const HEALTH_POLL_INTERVAL = 60000 // 60 秒轮询一次

const WsStateConfig: Record<string, { color: string; icon: React.ReactNode; label: string }> = {
  CONNECTING:   { color: '#FAAD14', icon: <SyncOutlined spin />, label: '连接中' },
  CONNECTED:    { color: '#52C41A', icon: <CheckCircleOutlined />, label: '已连接' },
  RECONNECTING: { color: '#FAAD14', icon: <SyncOutlined spin />, label: '重连中' },
  DISCONNECTED: { color: '#FF2A2A', icon: <CloseCircleOutlined />, label: '已断开' },
}

const SystemStatusBar = () => {
  const wsState = useSystemStatusStore((s) => s.wsState)
  const setWsState = useSystemStatusStore((s) => s.setWsState)
  const lastRealtimeAt = useSystemStatusStore((s) => s.lastRealtimeAt)
  const dataSource = useSystemStatusStore((s) => s.dataSource)
  const showDisconnectedBanner = useSystemStatusStore((s) => s.showDisconnectedBanner)
  const showRecoveredBanner = useSystemStatusStore((s) => s.showRecoveredBanner)
  const hideDisconnected = useSystemStatusStore((s) => s.hideDisconnected)
  const hideRecovered = useSystemStatusStore((s) => s.hideRecovered)
  const health = useSystemStatusStore((s) => s.health)
  const setHealth = useSystemStatusStore((s) => s.setHealth)
  const setHealthError = useSystemStatusStore((s) => s.setHealthError)

  const wsConnected = useDashboardStore((s) => s.wsConnected)
  const dashboardLastRealtimeAt = useDashboardStore((s) => s.lastRealtimeAt)

  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // 同步 WebSocket 连接状态到系统状态
  useEffect(() => {
    const state = wsConnected ? 'CONNECTED' : 'DISCONNECTED'
    setWsState(state)
  }, [wsConnected, setWsState])

  // 同步实时数据时间
  useEffect(() => {
    if (dashboardLastRealtimeAt > 0) {
      useSystemStatusStore.getState().setLastRealtimeAt(dashboardLastRealtimeAt)
    }
  }, [dashboardLastRealtimeAt])

  // 恢复横幅自动关闭
  useEffect(() => {
    if (!showRecoveredBanner) return
    const t = setTimeout(() => hideRecovered(), 5000)
    return () => clearTimeout(t)
  }, [showRecoveredBanner, hideRecovered])

  // 健康检查轮询
  const fetchHealth = useCallback(async () => {
    try {
      const data = await api.get('/system/health') as any
      setHealth({
        status: data?.status || null,
        mysql: data?.mysql || null,
        flask: data?.flask || null,
        llm: data?.llm || null,
        uptimeSeconds: data?.uptimeSeconds || 0,
      })
    } catch {
      setHealthError('健康检查失败')
    }
  }, [setHealth, setHealthError])

  useEffect(() => {
    fetchHealth()
    timerRef.current = setInterval(fetchHealth, HEALTH_POLL_INTERVAL)
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [fetchHealth])

  const handleReconnect = () => {
    // 触发 useWebSocket 重连：先断开，再触发重连
    setWsState('RECONNECTING')
    window.dispatchEvent(new CustomEvent('ws:reconnect'))
  }

  const wsCfg = WsStateConfig[wsState] || WsStateConfig.DISCONNECTED

  const dataAge = lastRealtimeAt > 0
    ? Math.floor((Date.now() - lastRealtimeAt) / 1000)
    : -1
  const dataStale = dataAge > 30 // 超过 30s 视为延迟

  const healthTag = (status: string | null | undefined, label: string) => {
    if (!status) return <Tag color="default">{label}: N/A</Tag>
    const isUp = status === 'UP'
    return (
      <Tag color={isUp ? 'green' : 'red'} icon={isUp ? <CheckCircleOutlined /> : <CloseCircleOutlined />}>
        {label}: {isUp ? '正常' : '异常'}
      </Tag>
    )
  }

  return (
    <div className="system-status-bar">
      {/* 正常状态条 */}
      <div className="system-status-bar__compact">
        <Space size="small" wrap>
          <Tooltip title={`WebSocket ${wsCfg.label}`}>
            <span style={{ color: wsCfg.color, fontSize: 11, display: 'inline-flex', alignItems: 'center', gap: 3 }}>
              {wsCfg.icon} WS {wsCfg.label}
            </span>
          </Tooltip>

          {lastRealtimeAt > 0 && (
            <Tooltip title={`最后数据: ${dayjs(lastRealtimeAt).format('MM-DD HH:mm:ss')}`}>
              <span style={{ color: dataStale ? '#FAAD14' : '#888', fontSize: 11, display: 'inline-flex', alignItems: 'center', gap: 3 }}>
                <ClockCircleOutlined />
                {dataStale ? `数据延迟 ${dataAge}s` : `${dataAge}s 前`}
              </span>
            </Tooltip>
          )}

          <span style={{ color: '#666', fontSize: 10 }}>源: {dataSource || '--'}</span>

          {health && (
            <>
              {healthTag(health.mysql, 'MySQL')}
              {healthTag(health.flask, 'Flask')}
              {health.llm && (
                <Tag color={health.llm.configured ? 'green' : 'default'}>
                  LLM: {health.llm.configured ? health.llm.model || '已配置' : '未配置'}
                </Tag>
              )}
            </>
          )}

          <Button
            type="text"
            size="small"
            icon={<ReloadOutlined />}
            onClick={fetchHealth}
            style={{ color: '#666', fontSize: 10, height: 20, padding: '0 4px' }}
            title="刷新健康状态"
          />
        </Space>
      </div>

      {/* 断连持久横幅 */}
      {showDisconnectedBanner && (
        <Alert
          type="error"
          showIcon
          icon={<CloseCircleOutlined />}
          message={
            <span>
              WebSocket 连接已断开 — <strong>当前数据可能不是最新状态</strong>
            </span>
          }
          action={
            <Button size="small" danger icon={<ReloadOutlined />} onClick={handleReconnect}>
              手动重连
            </Button>
          }
          banner
          closable
          onClose={hideDisconnected}
          className="system-status-bar__banner system-status-bar__banner--error"
        />
      )}

      {/* 恢复横幅 */}
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
        .system-status-bar__compact {
          padding: 4px 16px;
          background: #0c0c0c;
          border-bottom: 1px solid #1a1a1a;
          min-height: 28px;
          display: flex;
          align-items: center;
        }
        .system-status-bar__banner {
          border-radius: 0 !important;
          border-left: none !important;
          border-right: none !important;
        }
        .system-status-bar__banner--error {
          background: rgba(255,42,42,0.12) !important;
          border-color: #FF2A2A !important;
        }
        .system-status-bar__banner--success {
          background: rgba(82,196,26,0.12) !important;
          border-color: #52C41A !important;
        }
        @media (max-width: 390px) {
          .system-status-bar__compact {
            padding: 4px 8px;
          }
        }
      `}</style>
    </div>
  )
}

export default SystemStatusBar
