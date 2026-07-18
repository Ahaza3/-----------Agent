/**
 * 登录页面 — Phase 2 增强：语义化表单、内联错误、CapsLock提示、记住用户名、环境标识
 */
import { useState, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { Button, Card, Form, Input, Checkbox, Tag } from 'antd'
import { ThunderboltOutlined, WarningOutlined } from '@ant-design/icons'
import useAuthStore from '../../stores/useAuthStore'
import { getDefaultRoute } from '../../config/navigation'
import type { Role } from '../../config/roles'
import api from '../../services/api'

const REMEMBER_USERNAME_KEY = 'pl_remember_username'

const Login = () => {
  const [loading, setLoading] = useState(false)
  const [capsLock, setCapsLock] = useState(false)
  const [backendDown, setBackendDown] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const token = useAuthStore((s) => s.accessToken)
  const userRole = useAuthStore((s) => s.user?.role) as Role | undefined
  const setAuth = useAuthStore((s) => s.setAuth)
  const from = (location.state as any)?.from
  const [form] = Form.useForm()

  // 读取记住的用户名
  useEffect(() => {
    const saved = localStorage.getItem(REMEMBER_USERNAME_KEY)
    if (saved) form.setFieldValue('username', saved)
  }, [form])

  // 已登录跳转
  useEffect(() => {
    if (token) navigate(from || getDefaultRoute(userRole), { replace: true })
  }, [token, navigate, from, userRole])

  const doLogin = async (values: { username: string; password: string; remember: boolean }) => {
    setLoading(true)
    setBackendDown(false)
    try {
      const resp = await api.post('/auth/login', { username: values.username, password: values.password }) as any
      setAuth(resp.accessToken, resp.refreshToken, resp.user)

      // 记住用户名
      if (values.remember) {
        localStorage.setItem(REMEMBER_USERNAME_KEY, values.username)
      } else {
        localStorage.removeItem(REMEMBER_USERNAME_KEY)
      }

      const role = resp.user?.role as Role | undefined
      navigate(from || getDefaultRoute(role), { replace: true })
    } catch (err: any) {
      if (!err.response && err.message?.includes('Network')) {
        setBackendDown(true)
      } else {
        const msg = err?.response?.data?.message || err?.message || '登录失败'
        form.setFields([{ name: 'password', errors: [msg] }])
        form.setFieldValue('password', '')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      display: 'flex', justifyContent: 'center', alignItems: 'center',
      minHeight: '100vh', background: '#0B1117', padding: 16,
    }}>
      <Card style={{
        width: 400, border: '1px solid #1C2935', background: '#111A23',
        maxWidth: '100%', boxShadow: '0 24px 70px rgba(0,0,0,0.28)',
      }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <ThunderboltOutlined style={{ color: '#7FA7C7', fontSize: 34 }} />
          <h2 style={{ color: '#E7EDF3', margin: '14px 0 6px', fontSize: 20, fontWeight: 750 }}>电力负荷智能告警系统</h2>
          <p style={{ color: '#7D8A97', fontSize: 12, margin: 0 }}>运行监控、预测分析与告警处置</p>
          {import.meta.env.MODE === 'development' && (
            <div style={{ marginTop: 10 }}>
              <Tag color="default" style={{ fontSize: 11 }}>演示环境</Tag>
            </div>
          )}
        </div>

        {/* 后端不可用 */}
        {backendDown && (
          <div style={{
            padding: '8px 12px', marginBottom: 16,
            border: '1px solid #D7A447', background: 'rgba(215,164,71,0.1)',
            display: 'flex', alignItems: 'center', gap: 8,
          }}>
            <WarningOutlined style={{ color: '#D7A447' }} />
            <span style={{ color: '#D7A447', fontSize: 12 }}>服务暂不可用，请稍后重试</span>
          </div>
        )}

        <Form form={form} layout="vertical" onFinish={doLogin}
          initialValues={{ remember: true, username: '' }}
          style={{ marginBottom: 0 }}
        >
          <Form.Item
            label={<span style={{ color: '#9CAAB7', fontSize: 12 }}>用户名</span>}
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input
              autoComplete="username"
              placeholder="请输入用户名"
              style={{ background: '#0E1620', borderColor: '#2A3949', color: '#E7EDF3', height: 38 }}
              onKeyDown={(e) => { setCapsLock(e.getModifierState('CapsLock')) }}
              disabled={loading}
            />
          </Form.Item>

          <Form.Item
            label={<span style={{ color: '#9CAAB7', fontSize: 12 }}>密码</span>}
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              autoComplete="current-password"
              placeholder="请输入密码"
              style={{ background: '#0E1620', borderColor: '#2A3949', color: '#E7EDF3', height: 38 }}
              onKeyDown={(e) => {
                setCapsLock(e.getModifierState('CapsLock'))
                if (e.key === 'Enter' && !loading) form.submit()
              }}
              disabled={loading}
            />
          </Form.Item>

          {/* Caps Lock 提示 */}
          {capsLock && (
            <div style={{
              padding: '4px 8px', marginBottom: 12, fontSize: 11,
              color: '#D7A447', display: 'flex', alignItems: 'center', gap: 6,
            }}>
              <WarningOutlined /> 大写锁定已开启
            </div>
          )}

          {/* 记住用户名 */}
          <Form.Item name="remember" valuePropName="checked" style={{ marginBottom: 16 }}>
            <Checkbox style={{ color: '#9CAAB7', fontSize: 12 }}>记住用户名</Checkbox>
          </Form.Item>

          <Form.Item style={{ marginBottom: 0 }}>
            <Button
              type="primary"
              htmlType="submit"
              block
              loading={loading}
              disabled={loading}
              style={{ height: 38, fontWeight: 600 }}
            >
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}

export default Login
