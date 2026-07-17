/**
 * 主布局 — Brutalist CRT Terminal 风格
 * 支持桌面端折叠侧栏 + 移动端 Drawer 菜单
 * 角色化导航项 + 顶栏连接状态
 */
import { useState, useEffect, useMemo } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout, Menu, Button, Typography, Space, Badge, Tooltip, Tag, Drawer } from 'antd'
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ThunderboltOutlined,
  LogoutOutlined,
  UserOutlined,
  MenuOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import useDashboardStore from '../stores/useDashboardStore'
import useAuthStore from '../stores/useAuthStore'
import { getMenuItems } from '../config/roles'
import type { Role } from '../config/roles'
import SystemStatusBar from '../components/SystemStatusBar'

const { Header, Sider, Content } = Layout
const { Text } = Typography

const SIDER_WIDTH = 220
const SIDER_COLLAPSED_WIDTH = 56
const MOBILE_BREAKPOINT = 960

const MainLayout = () => {
  const [collapsed, setCollapsed] = useState(false)
  const [clock, setClock] = useState(dayjs().format('YYYY-MM-DD HH:mm:ss'))
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false)
  const [isMobile, setIsMobile] = useState(window.innerWidth < MOBILE_BREAKPOINT)
  const navigate = useNavigate()
  const location = useLocation()
  const user = useAuthStore((s) => s.user)
  const role = user?.role as Role | undefined
  const logout = useAuthStore((s) => s.logout)
  const wsConnected = useDashboardStore((s) => s.wsConnected)
  const unreadAlerts = useDashboardStore(
    (state) => state.alerts.filter((alert) => alert.isRead === 0).length,
  )

  const menuItems = useMemo(() => getMenuItems(role), [role])

  const showAlertBadge = role === 'DISPATCHER'

  useEffect(() => {
    const timer = setInterval(() => {
      setClock(dayjs().format('YYYY-MM-DD HH:mm:ss'))
    }, 1000)
    return () => clearInterval(timer)
  }, [])

  useEffect(() => {
    const handleResize = () => {
      setIsMobile(window.innerWidth < MOBILE_BREAKPOINT)
      if (window.innerWidth >= MOBILE_BREAKPOINT) {
        setMobileDrawerOpen(false)
      }
    }
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  const selectedKey = '/' + location.pathname.split('/')[1]

  const currentPageLabel = useMemo(() => {
    if (menuItems.length === 0) return ''
    const found = menuItems.find((item: any) => item?.key === selectedKey)
    return found ? (found as any).label : ''
  }, [menuItems, selectedKey])

  const handleMenuClick = (info: { key: string }) => {
    navigate(info.key)
    if (isMobile) setMobileDrawerOpen(false)
  }

  const menuNode = (
    <Menu
      theme="dark"
      mode="inline"
      selectedKeys={[selectedKey]}
      items={menuItems}
      onClick={handleMenuClick}
      style={{ borderInlineEnd: 'none', marginTop: 4 }}
    />
  )

  return (
    <Layout className="main-layout">
      {/* ====== 桌面侧栏 ====== */}
      {!isMobile && (
        <Sider
          width={SIDER_WIDTH}
          collapsedWidth={SIDER_COLLAPSED_WIDTH}
          collapsible
          collapsed={collapsed}
          trigger={null}
          className="main-layout__sider"
        >
          <div className={`main-layout__logo ${collapsed ? 'collapsed' : ''}`}>
            <ThunderboltOutlined className="main-layout__logo-icon" />
            {!collapsed && <span className="main-layout__logo-text font-mono">电力负荷监控</span>}
          </div>
          {menuNode}
        </Sider>
      )}

      {/* ====== 右侧 ====== */}
      <Layout>
        {/* 顶栏 */}
        <Header className="main-layout__header">
          <div className="main-layout__header-left">
            {isMobile ? (
              <Button
                type="text"
                icon={<MenuOutlined />}
                onClick={() => setMobileDrawerOpen(true)}
                className="main-layout__icon-btn"
              />
            ) : (
              <Button
                type="text"
                icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={() => setCollapsed(!collapsed)}
                className="main-layout__icon-btn"
              />
            )}
            {isMobile && (
              <span className="main-layout__page-title">{currentPageLabel || '电力负荷监控'}</span>
            )}
          </div>

          <Space size="middle" className="main-layout__header-right">
            {/* 连接状态指示 */}
            <Tooltip title={wsConnected ? '实时连接正常' : '实时连接断开'}>
              <span className="font-mono" style={{
                color: wsConnected ? '#4AF626' : '#FF2A2A',
                fontSize: 11,
                letterSpacing: '0.05em',
              }}>
                {wsConnected ? '● 已连接' : '○ 断开'}
              </span>
            </Tooltip>

            {/* 告警按钮 — 仅调度员 */}
            {showAlertBadge && (
              <Tooltip title="未读告警">
                <Badge count={unreadAlerts} size="small">
                  <Button
                    type="text"
                    className="main-layout__icon-btn"
                    onClick={() => navigate('/alerts')}
                    style={{ color: unreadAlerts > 0 ? '#FF2A2A' : '#666666' }}
                  >
                    <span style={{ fontSize: 12 }}>🔔</span>
                  </Button>
                </Badge>
              </Tooltip>
            )}

            {user && (
              <>
                <Tag icon={<UserOutlined />} color="default" style={{ fontSize: 11 }}>
                  {user.displayName || user.username}
                </Tag>
                <Button type="text" icon={<LogoutOutlined />} size="small"
                  onClick={() => { logout(); navigate('/login') }}
                  className="main-layout__icon-btn"
                  style={{ color: '#888' }}>退出</Button>
              </>
            )}
            {!isMobile && (
              <Text className="font-mono main-layout__clock">
                {clock}
              </Text>
            )}
          </Space>
        </Header>

        {/* 全局状态条 */}
        <SystemStatusBar />

        {/* 内容区 */}
        <Content className="main-layout__content">
          <Outlet />
        </Content>
      </Layout>

      {/* ====== 移动端 Drawer ====== */}
      {isMobile && (
        <Drawer
          placement="left"
          open={mobileDrawerOpen}
          onClose={() => setMobileDrawerOpen(false)}
          width={SIDER_WIDTH}
          styles={{
            body: { padding: 0, background: '#141414' },
            header: { background: '#141414', borderBottom: '2px solid #2A2A2A' },
          }}
          title={
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <ThunderboltOutlined style={{ color: '#FF2A2A', fontSize: 16 }} />
              <span className="font-mono" style={{ color: '#EAEAEA', fontSize: 12 }}>电力负荷监控</span>
            </div>
          }
          closeIcon={null}
        >
          {menuNode}
        </Drawer>
      )}

      <style>{`
        .main-layout {
          min-height: 100vh;
        }
        .main-layout__sider {
          border-right: 2px solid #2A2A2A;
        }
        .main-layout__logo {
          height: 52px;
          display: flex;
          align-items: center;
          justify-content: center;
          border-bottom: 2px solid #2A2A2A;
          gap: 6px;
          padding: 0 16px;
        }
        .main-layout__logo.collapsed {
          padding: 0;
          gap: 0;
        }
        .main-layout__logo-icon {
          color: #FF2A2A;
          font-size: 16px;
        }
        .collapsed .main-layout__logo-icon {
          font-size: 20px;
        }
        .main-layout__logo-text {
          color: #EAEAEA;
          font-size: 12px;
          letter-spacing: 0.1em;
          text-transform: uppercase;
          white-space: nowrap;
        }
        .main-layout__header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 0 16px;
          border-bottom: 2px solid #2A2A2A;
          height: 48px;
          line-height: 48px;
          flex-wrap: wrap;
          min-width: 0;
        }
        .main-layout__header-left {
          display: flex;
          align-items: center;
          gap: 8px;
          min-width: 0;
          flex: 0 1 auto;
        }
        .main-layout__header-right {
          display: flex;
          align-items: center;
          flex-wrap: nowrap;
          min-width: 0;
          flex-shrink: 0;
        }
        .main-layout__icon-btn {
          color: #888888;
          font-size: 16px;
        }
        .main-layout__page-title {
          color: #EAEAEA;
          font-size: 14px;
          font-weight: 700;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .main-layout__clock {
          color: #AAAAAA;
          font-size: 12px;
          letter-spacing: 0.06em;
          white-space: nowrap;
        }
        .main-layout__content {
          padding: 16px;
          overflow: auto;
          min-width: 0;
        }
        @media (max-width: 960px) {
          .main-layout__content {
            padding: 12px;
          }
          .main-layout__header {
            padding: 0 12px;
          }
        }
        @media (max-width: 390px) {
          .main-layout__header {
            padding: 0 8px;
          }
          .main-layout__content {
            padding: 8px;
          }
          .main-layout__header-right {
            gap: 4px !important;
          }
        }
      `}</style>
    </Layout>
  )
}

export default MainLayout
