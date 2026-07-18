/**
 * 主布局：调度工作台导航框架。
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
  BellOutlined,
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

  const roleLabel = role === 'DISPATCHER'
    ? '调度员'
    : role === 'OPERATOR'
      ? '运维'
      : role === 'SYSTEM_ADMIN'
        ? '管理员'
        : '用户'

  return (
    <Layout className="main-layout">
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
            {!collapsed && (
              <span className="main-layout__logo-text">
                负荷调度工作台
                <small>Load Operations</small>
              </span>
            )}
          </div>
          {menuNode}
        </Sider>
      )}

      <Layout>
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
              <span className="main-layout__page-title">{currentPageLabel || '负荷调度工作台'}</span>
            )}
            {!isMobile && (
              <span className="main-layout__page-title">{currentPageLabel || '运行监控'}</span>
            )}
          </div>

          <Space size="middle" className="main-layout__header-right">
            {showAlertBadge && (
              <Tooltip title="未读告警">
                <Badge count={unreadAlerts} size="small">
                  <Button
                    type="text"
                    className="main-layout__icon-btn"
                    onClick={() => navigate('/alerts')}
                    icon={<BellOutlined />}
                    style={{ color: unreadAlerts > 0 ? '#D85C5C' : '#9CAAB7' }}
                  >
                  </Button>
                </Badge>
              </Tooltip>
            )}

            {user && (
              <>
                <Tag icon={<UserOutlined />} color="default" className="main-layout__user-tag">
                  {user.displayName || user.username}
                  <span className="main-layout__user-role">· {roleLabel}</span>
                </Tag>
                <Button type="text" icon={<LogoutOutlined />} size="small"
                  onClick={() => { logout(); navigate('/login') }}
                  className="main-layout__icon-btn"
                  style={{ color: '#9CAAB7' }}>退出</Button>
              </>
            )}
            {!isMobile && (
              <Text className="font-mono main-layout__clock">
                {clock}
              </Text>
            )}
          </Space>
        </Header>

        <SystemStatusBar />

        <Content className="main-layout__content">
          <Outlet />
        </Content>
      </Layout>

      {isMobile && (
        <Drawer
          placement="left"
          open={mobileDrawerOpen}
          onClose={() => setMobileDrawerOpen(false)}
          width={SIDER_WIDTH}
          styles={{
            body: { padding: 0, background: '#0F1720' },
            header: { background: '#0F1720', borderBottom: '1px solid #253341' },
          }}
          title={
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <ThunderboltOutlined style={{ color: '#7FA7C7', fontSize: 16 }} />
              <span style={{ color: '#E7EDF3', fontSize: 13 }}>负荷调度工作台</span>
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
          background: #0b1117;
        }
        .main-layout__sider {
          border-right: 1px solid #1c2935;
          box-shadow: 10px 0 28px rgba(0, 0, 0, 0.16);
        }
        .main-layout__logo {
          height: 64px;
          display: flex;
          align-items: center;
          justify-content: flex-start;
          border-bottom: 1px solid #1c2935;
          gap: 10px;
          padding: 0 16px;
        }
        .main-layout__logo.collapsed {
          padding: 0;
          gap: 0;
        }
        .main-layout__logo-icon {
          color: #7FA7C7;
          font-size: 18px;
        }
        .collapsed .main-layout__logo-icon {
          font-size: 20px;
        }
        .main-layout__logo-text {
          color: #E7EDF3;
          font-size: 14px;
          font-weight: 700;
          line-height: 1.15;
          white-space: nowrap;
        }
        .main-layout__logo-text small {
          display: block;
          margin-top: 4px;
          color: #6F7D8A;
          font-size: 10px;
          font-weight: 500;
        }
        .main-layout__header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 0 18px;
          border-bottom: 1px solid #1c2935;
          height: 56px;
          line-height: 56px;
          flex-wrap: wrap;
          min-width: 0;
          background: rgba(16, 25, 35, 0.94);
          backdrop-filter: blur(12px);
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
          color: #9CAAB7;
          font-size: 16px;
        }
        .main-layout__page-title {
          color: #E7EDF3;
          font-size: 14px;
          font-weight: 650;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .main-layout__user-tag {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          font-size: 12px;
        }
        .main-layout__user-role {
          color: #6F7D8A;
          font-size: 11px;
        }
        .main-layout__clock {
          color: #9CAAB7;
          font-size: 12px;
          white-space: nowrap;
        }
        .main-layout__content {
          padding: 18px;
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
