import { useState, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { Button, Card, Input, message } from 'antd'
import { ThunderboltOutlined } from '@ant-design/icons'
import useAuthStore from '../../stores/useAuthStore'
import api from '../../services/api'

const Login = () => {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const token = useAuthStore((s) => s.accessToken)
  const setAuth = useAuthStore((s) => s.setAuth)
  const from = (location.state as any)?.from || '/dashboard'

  // 已登录则直接跳转
  useEffect(() => { if (token) navigate(from, { replace: true }) }, [token, navigate, from])

  const doLogin = async () => {
    if (!username || !password) { message.warning('请输入用户名和密码'); return }
    setLoading(true)
    try {
      const resp = await api.post('/auth/login', { username, password }) as any
      setAuth(resp.accessToken, resp.refreshToken, resp.user)
      navigate(from, { replace: true })
    } catch (err: any) {
      message.error(err?.response?.data?.message || err?.message || '登录失败')
    } finally { setLoading(false) }
  }

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', background: '#0a0a0a' }}>
      <Card style={{ width: 380, border: '1px solid #2A2A2A', background: '#0e0e0e' }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <ThunderboltOutlined style={{ color: '#FF2A2A', fontSize: 32 }} />
          <h2 style={{ color: '#EAEAEA', margin: '12px 0 4px', fontSize: 18 }}>电力负荷智能告警系统</h2>
          <p style={{ color: '#666', fontSize: 12 }}>请登录以继续</p>
        </div>
        <Input placeholder="用户名" value={username} onChange={(e) => setUsername(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && doLogin()}
          style={{ marginBottom: 12, background: '#0a0a0a', borderColor: '#2A2A2A', color: '#EAEAEA' }} />
        <Input.Password placeholder="密码" value={password} onChange={(e) => setPassword(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && doLogin()}
          style={{ marginBottom: 20, background: '#0a0a0a', borderColor: '#2A2A2A', color: '#EAEAEA' }} />
        <Button type="primary" block loading={loading} onClick={doLogin}
          style={{ height: 38, fontWeight: 600 }}>登录</Button>
      </Card>
    </div>
  )
}

export default Login
