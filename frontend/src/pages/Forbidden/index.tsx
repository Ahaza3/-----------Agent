import { useNavigate } from 'react-router-dom'
import { Result, Button } from 'antd'
import { LockOutlined } from '@ant-design/icons'
import useAuthStore from '../../stores/useAuthStore'
import { getDefaultRoute } from '../../config/navigation'
import type { Role } from '../../config/roles'

/** 403 无权限页面 */
const Forbidden = () => {
  const navigate = useNavigate()
  const role = useAuthStore((s) => s.user?.role) as Role | undefined

  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      minHeight: '100vh', background: '#0a0a0a',
    }}>
      <Result
        status="403"
        title={<span style={{ color: '#EAEAEA' }}>403 — 无访问权限</span>}
        subTitle={<span style={{ color: '#888' }}>当前角色没有访问该页面的权限</span>}
        icon={<LockOutlined style={{ color: '#FF2A2A' }} />}
        extra={
          <Button
            onClick={() => navigate(getDefaultRoute(role))}
            style={{ borderRadius: 0 }}
          >
            返回首页
          </Button>
        }
        style={{ color: '#EAEAEA' }}
      />
    </div>
  )
}

export default Forbidden
