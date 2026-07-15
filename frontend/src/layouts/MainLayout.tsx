/**
 * 主布局 — Brutalist CRT Terminal 风格
 * 可见边框分隔 + 等宽时钟 + ASCII 标识
 */
import { useState, useEffect } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout, Menu, Button, Typography, Space } from 'antd'
import {
  DashboardOutlined,
  AlertOutlined,
  RobotOutlined,
  DatabaseOutlined,
  SettingOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'

const { Header, Sider, Content } = Layout
const { Text } = Typography

const menuItems = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: '可视化大屏' },
  { key: '/alerts', icon: <AlertOutlined />, label: '告警中心' },
  { key: '/agent', icon: <RobotOutlined />, label: '智能助手' },
  { key: '/data', icon: <DatabaseOutlined />, label: '数据查询' },
  { key: '/admin', icon: <SettingOutlined />, label: '系统管理' },
]

const SIDER_WIDTH = 220
const SIDER_COLLAPSED_WIDTH = 56

const MainLayout = () => {
  const [collapsed, setCollapsed] = useState(false)
  const [clock, setClock] = useState(dayjs().format('YYYY-MM-DD HH:mm:ss'))
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => {
    const timer = setInterval(() => {
      setClock(dayjs().format('YYYY-MM-DD HH:mm:ss'))
    }, 1000)
    return () => clearInterval(timer)
  }, [])

  const selectedKey = '/' + location.pathname.split('/')[1]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* ====== 侧栏 ====== */}
      <Sider
        width={SIDER_WIDTH}
        collapsedWidth={SIDER_COLLAPSED_WIDTH}
        collapsible
        collapsed={collapsed}
        trigger={null}
        style={{
          borderRight: '2px solid #2A2A2A',
        }}
      >
        {/* Logo */}
        <div
          style={{
            height: 52,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderBottom: '2px solid #2A2A2A',
            gap: collapsed ? 0 : 6,
            padding: collapsed ? 0 : '0 16px',
          }}
        >
          <ThunderboltOutlined
            style={{ fontSize: collapsed ? 20 : 16, color: '#FF2A2A' }}
          />
          {!collapsed && (
            <span
              className="font-mono"
              style={{
                color: '#EAEAEA',
                fontSize: 12,
                letterSpacing: '0.1em',
                textTransform: 'uppercase',
                whiteSpace: 'nowrap',
              }}
            >
              电力负荷监控
            </span>
          )}
        </div>

        {/* 菜单 */}
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={(info) => navigate(info.key)}
          style={{ borderInlineEnd: 'none', marginTop: 4 }}
        />
      </Sider>

      {/* ====== 右侧 ====== */}
      <Layout>
        {/* 顶部栏 */}
        <Header
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '0 20px',
            borderBottom: '2px solid #2A2A2A',
          }}
        >
          <Button
            type="text"
            icon={
              collapsed ? (
                <MenuUnfoldOutlined />
              ) : (
                <MenuFoldOutlined />
              )
            }
            onClick={() => setCollapsed(!collapsed)}
            style={{ color: '#888888', fontSize: 16 }}
          />

          <Space size="middle">
            <span
              className="font-mono"
              style={{ color: '#4AF626', fontSize: 11, letterSpacing: '0.05em' }}
            >
              系统运行中
            </span>
            <Text
              className="font-mono"
              style={{
                color: '#AAAAAA',
                fontSize: 12,
                letterSpacing: '0.06em',
              }}
            >
              {clock}
            </Text>
          </Space>
        </Header>

        {/* 内容区 */}
        <Content style={{ padding: 20, overflow: 'auto' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}

export default MainLayout
