/**
 * 主布局 — 顶部导航 + 侧边菜单 + 内容区
 * 暗色大屏风格，适配可视化监控场景
 */
import { useState } from 'react'
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
  ClockCircleOutlined,
} from '@ant-design/icons'
import { useEffect } from 'react'
import dayjs from 'dayjs'

const { Header, Sider, Content } = Layout
const { Text } = Typography

// ---- 菜单项配置 ----
const menuItems = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: '可视化大屏' },
  { key: '/alerts', icon: <AlertOutlined />, label: '告警中心' },
  { key: '/agent', icon: <RobotOutlined />, label: '智能助手' },
  { key: '/data', icon: <DatabaseOutlined />, label: '数据查询' },
  { key: '/admin', icon: <SettingOutlined />, label: '系统管理' },
]

const SIDER_WIDTH = 220
const SIDER_COLLAPSED_WIDTH = 64

const MainLayout = () => {
  const [collapsed, setCollapsed] = useState(false)
  const [clock, setClock] = useState(dayjs().format('YYYY-MM-DD HH:mm:ss'))
  const navigate = useNavigate()
  const location = useLocation()

  // 实时时钟
  useEffect(() => {
    const timer = setInterval(() => {
      setClock(dayjs().format('YYYY-MM-DD HH:mm:ss'))
    }, 1000)
    return () => clearInterval(timer)
  }, [])

  // 菜单选中
  const selectedKey = '/' + location.pathname.split('/')[1]

  const handleMenuClick = (info: { key: string }) => {
    navigate(info.key)
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* ---- 侧边栏 ---- */}
      <Sider
        width={SIDER_WIDTH}
        collapsedWidth={SIDER_COLLAPSED_WIDTH}
        collapsible
        collapsed={collapsed}
        trigger={null}
        style={{
          borderRight: '1px solid #162050',
          overflow: 'auto',
        }}
      >
        {/* Logo 区域 */}
        <div
          style={{
            height: 56,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderBottom: '1px solid #162050',
            gap: collapsed ? 0 : 8,
            padding: collapsed ? 0 : '0 16px',
          }}
        >
          <ThunderboltOutlined
            style={{
              fontSize: collapsed ? 22 : 20,
              color: '#4f8cff',
            }}
          />
          {!collapsed && (
            <Text
              strong
              style={{
                color: '#e0e6f0',
                fontSize: 16,
                whiteSpace: 'nowrap',
              }}
            >
              电力负荷监控
            </Text>
          )}
        </div>

        {/* 菜单 */}
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={handleMenuClick}
          style={{ borderInlineEnd: 'none', marginTop: 8 }}
        />
      </Sider>

      {/* ---- 右侧区域 ---- */}
      <Layout>
        {/* 顶部导航 */}
        <Header
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '0 24px',
            borderBottom: '1px solid #162050',
          }}
        >
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
            style={{ color: '#8892a4', fontSize: 16 }}
          />

          <Space size="large">
            <ClockCircleOutlined style={{ color: '#4f8cff' }} />
            <Text style={{ color: '#8892a4', fontFamily: 'monospace', fontSize: 14 }}>
              {clock}
            </Text>
          </Space>
        </Header>

        {/* 内容区 */}
        <Content
          style={{
            padding: 24,
            overflow: 'auto',
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}

export default MainLayout
