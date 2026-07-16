import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Progress, Space, Tag, message } from 'antd'
import {
  AlertOutlined,
  CheckCircleOutlined,
  DashboardOutlined,
  RiseOutlined,
  RollbackOutlined,
  SettingOutlined,
} from '@ant-design/icons'
import {
  fetchDemoLoadStatus,
  setDemoLoadMode,
  type DemoLoadStatus,
} from '../../services/demoApi'

const MODE_CONFIG = {
  NORMAL: { label: '正常运行', color: '#4AF626' },
  SPIKE: { label: '负荷突增', color: '#FF2A2A' },
  RECOVERY: { label: '恢复中', color: '#FADB14' },
} as const

const Admin = () => {
  const navigate = useNavigate()
  const [status, setStatus] = useState<DemoLoadStatus | null>(null)
  const [available, setAvailable] = useState<boolean | null>(null)
  const [commanding, setCommanding] = useState(false)

  const refresh = useCallback(async () => {
    try {
      const next = await fetchDemoLoadStatus()
      setStatus(next)
      setAvailable(true)
    } catch {
      setAvailable(false)
    }
  }, [])

  useEffect(() => {
    refresh()
    const timer = window.setInterval(refresh, 1_000)
    return () => window.clearInterval(timer)
  }, [refresh])

  const runCommand = async (command: 'normal' | 'spike' | 'recover') => {
    setCommanding(true)
    try {
      const next = await setDemoLoadMode(command)
      setStatus(next)
      const labels = {
        normal: '已复位到正常运行基线',
        spike: '负荷突增场景已启动',
        recover: '负荷恢复场景已启动',
      }
      message.success(labels[command])
    } catch {
      message.error('演示命令执行失败')
    } finally {
      setCommanding(false)
    }
  }

  const mode = status ? MODE_CONFIG[status.mode] : MODE_CONFIG.NORMAL
  const progress = useMemo(() => {
    if (!status || status.redThreshold <= 0) return 0
    return Math.min(100, status.currentLoad / status.redThreshold * 100)
  }, [status])

  return (
    <div className="admin-root">
      <div className="admin-toolbar">
        <h1>
          <SettingOutlined />
          系统管理
          <span>//</span>
          <small>开发环境演示控制</small>
        </h1>
        <Button icon={<AlertOutlined />} onClick={() => navigate('/alerts')}>
          查看告警中心
        </Button>
      </div>

      <hr className="brutalist" />

      <section className="demo-console">
        <div className="demo-console__header">
          <div>
            <div className="section-label">负荷异常演示模式</div>
            <div className="section-value">实时告警全链路</div>
          </div>
          {available === false ? (
            <Tag color="default">演示接口未启用</Tag>
          ) : (
            <Tag color={mode.color}>{mode.label}</Tag>
          )}
        </div>

        {available !== false && status && (
          <>
            <div className="demo-metrics">
              <div>
                <span>当前负荷</span>
                <strong>{status.currentLoad.toFixed(1)} <small>MW</small></strong>
              </div>
              <div>
                <span>场景目标</span>
                <strong>{status.targetLoad.toFixed(0)} <small>MW</small></strong>
              </div>
              <div>
                <span>正常基线</span>
                <strong>{status.normalTargetLoad.toFixed(0)} <small>MW</small></strong>
              </div>
            </div>

            <Progress
              percent={progress}
              showInfo={false}
              strokeColor={mode.color}
              trailColor="#1A1A1A"
              strokeLinecap="butt"
            />

            <div className="threshold-grid">
              <div style={{ borderColor: '#FADB14' }}>
                <span>黄色阈值</span>
                <strong>{status.yellowThreshold.toFixed(0)} MW</strong>
              </div>
              <div style={{ borderColor: '#FA8C16' }}>
                <span>橙色阈值</span>
                <strong>{status.orangeThreshold.toFixed(0)} MW</strong>
              </div>
              <div style={{ borderColor: '#FF2A2A' }}>
                <span>红色阈值</span>
                <strong>{status.redThreshold.toFixed(0)} MW</strong>
              </div>
            </div>

            <Space wrap size={8} className="demo-actions">
              <Button
                icon={<CheckCircleOutlined />}
                loading={commanding}
                onClick={() => runCommand('normal')}
              >
                正常运行
              </Button>
              <Button
                type="primary"
                danger
                icon={<RiseOutlined />}
                loading={commanding}
                disabled={status.mode === 'SPIKE'}
                onClick={() => runCommand('spike')}
              >
                模拟负荷突增
              </Button>
              <Button
                icon={<RollbackOutlined />}
                loading={commanding}
                disabled={status.mode === 'NORMAL'}
                onClick={() => runCommand('recover')}
              >
                恢复正常
              </Button>
              <Button icon={<DashboardOutlined />} onClick={() => navigate('/dashboard')}>
                查看实时曲线
              </Button>
            </Space>
          </>
        )}
      </section>

      <style>{`
        .admin-root { min-height: calc(100vh - 132px); }
        .admin-toolbar {
          display: flex; align-items: center; justify-content: space-between;
          gap: 12px; flex-wrap: wrap; margin-bottom: 8px;
        }
        .admin-toolbar h1 {
          margin: 0; color: #EAEAEA; font-size: 16px; font-weight: 900;
        }
        .admin-toolbar h1 > .anticon { color: #FF2A2A; margin-right: 8px; }
        .admin-toolbar h1 span { color: #666; margin: 0 6px; font-size: 12px; }
        .admin-toolbar h1 small { color: #888; font-size: 11px; font-weight: 400; }
        .demo-console { border: 1px solid #2A2A2A; margin-top: 14px; }
        .demo-console__header {
          min-height: 72px; padding: 14px 16px; border-bottom: 1px solid #2A2A2A;
          display: flex; align-items: center; justify-content: space-between; gap: 12px;
        }
        .section-label { color: #888; font-size: 11px; font-family: monospace; }
        .section-value { color: #EAEAEA; font-size: 18px; font-weight: 700; margin-top: 4px; }
        .demo-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); }
        .demo-metrics > div { padding: 20px 16px; border-right: 1px solid #2A2A2A; }
        .demo-metrics > div:last-child { border-right: 0; }
        .demo-metrics span, .threshold-grid span { display: block; color: #888; font-size: 11px; }
        .demo-metrics strong { display: block; color: #EAEAEA; font-size: 28px; margin-top: 5px; }
        .demo-metrics small { color: #777; font-size: 11px; }
        .demo-console .ant-progress { display: block; padding: 0 16px; margin: 2px 0 18px; }
        .threshold-grid {
          display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px;
          padding: 0 16px 16px;
        }
        .threshold-grid > div { border-left: 3px solid; padding: 8px 10px; background: #0E0E0E; }
        .threshold-grid strong { display: block; color: #EAEAEA; font-size: 14px; margin-top: 3px; }
        .demo-actions { padding: 14px 16px; border-top: 1px solid #2A2A2A; width: 100%; }
        @media (max-width: 720px) {
          .demo-metrics, .threshold-grid { grid-template-columns: 1fr; }
          .demo-metrics > div { border-right: 0; border-bottom: 1px solid #2A2A2A; }
        }
      `}</style>
    </div>
  )
}

export default Admin
