import { useNavigate } from 'react-router-dom'
import { Result, Button } from 'antd'
import useAuthStore from '../../stores/useAuthStore'
import { getDefaultRoute } from '../../config/navigation'
import type { Role } from '../../config/roles'

/** 404 页面未找到 */
const NotFound = () => {
  const navigate = useNavigate()
  const role = useAuthStore((s) => s.user?.role) as Role | undefined

  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      minHeight: '100vh', background: '#0a0a0a',
    }}>
      <Result
        status="404"
        title={<span style={{ color: '#EAEAEA' }}>404 — 页面未找到</span>}
        subTitle={<span style={{ color: '#888' }}>请求的页面不存在或已被移除</span>}
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

export default NotFound
