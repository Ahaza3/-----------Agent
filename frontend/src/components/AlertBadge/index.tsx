/**
 * AlertBadge — 顶部告警指示灯
 * 显示未读告警数量，收到新告警时闪烁
 */
import { useEffect, useState } from 'react'
import { Badge } from 'antd'
import { BellOutlined } from '@ant-design/icons'
import useDashboardStore from '../../stores/useDashboardStore'

const AlertBadge = () => {
  const alerts = useDashboardStore((s) => s.alerts)
  const unread = alerts.filter((a) => a.isRead === 0).length
  const [flashing, setFlashing] = useState(false)

  // 新告警到达时触发闪烁
  useEffect(() => {
    if (unread > 0) {
      setFlashing(true)
      const timer = setTimeout(() => setFlashing(false), 2000)
      return () => clearTimeout(timer)
    }
  }, [unread])

  return (
    <Badge count={unread} size="small" offset={[4, -2]}>
      <BellOutlined
        style={{
          fontSize: 18,
          color: flashing ? '#FF2A2A' : unread > 0 ? '#E6C300' : '#888888',
          animation: flashing ? 'alert-flash 0.3s ease-out 3' : undefined,
        }}
      />
      <style>{`
        @keyframes alert-flash {
          0%, 100% { opacity: 1; transform: scale(1); }
          50% { opacity: 0.3; transform: scale(1.3); }
        }
      `}</style>
    </Badge>
  )
}

export default AlertBadge
