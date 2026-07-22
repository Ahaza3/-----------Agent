/**
 * 集中角色与权限配置
 * 禁止在各页面散落 role === 'DISPATCHER' / 'OPERATOR' / 'SYSTEM_ADMIN' 硬编码
 */
import type { MenuProps } from 'antd'
import {
  DashboardOutlined,
  AlertOutlined,
  RobotOutlined,
  DatabaseOutlined,
  SettingOutlined,
  UserOutlined,
  AuditOutlined,
  HeartOutlined,
  ThunderboltOutlined,
  FileTextOutlined,
  ApartmentOutlined,
} from '@ant-design/icons'

export type Role = 'DISPATCHER' | 'OPERATOR' | 'SYSTEM_ADMIN'

/* ─── 路由定义 ─── */
export interface RouteDef {
  path: string
  allowedRoles: Role[]
}

export const PROTECTED_ROUTES: Record<string, RouteDef> = {
  dashboard: { path: '/dashboard', allowedRoles: ['DISPATCHER', 'OPERATOR'] },
  alerts:    { path: '/alerts',    allowedRoles: ['DISPATCHER', 'OPERATOR'] },
  agent:     { path: '/agent',     allowedRoles: ['DISPATCHER', 'OPERATOR', 'SYSTEM_ADMIN'] },
  data:      { path: '/data',      allowedRoles: ['DISPATCHER', 'OPERATOR', 'SYSTEM_ADMIN'] },
  topology:  { path: '/topology',  allowedRoles: ['DISPATCHER', 'OPERATOR', 'SYSTEM_ADMIN'] },
  admin:     { path: '/admin',     allowedRoles: ['OPERATOR', 'SYSTEM_ADMIN'] },
}

/* ─── 角色中文名 & 默认首页 ─── */
interface RoleMeta {
  label: string
  defaultRoute: string
  /** 可访问路由路径列表 */
  accessibleRoutes: string[]
  /** 是否在顶栏显示告警通知按钮 */
  showAlertBadge: boolean
  /** Admin Tabs 可见的 tab key 列表 */
  adminTabs: string[]
  /** 工单操作权限映射 */
  ticketActions: Record<string, boolean>
}

export const ROLE_CONFIG: Record<Role, RoleMeta> = {
  DISPATCHER: {
    label: '电力调度员',
    defaultRoute: '/dashboard',
    accessibleRoutes: ['/dashboard', '/alerts', '/agent', '/data', '/topology'],
    showAlertBadge: true,
    adminTabs: [],
    ticketActions: {
      create: true,
      assign: true,
      claim: false,
      start: false,
      resolve: false,
      close: true,
      cancel: true,
    },
  },
  OPERATOR: {
    label: '运维管理员',
    defaultRoute: '/dashboard',
    accessibleRoutes: ['/dashboard', '/alerts', '/agent', '/data', '/topology', '/admin'],
    showAlertBadge: false,
    adminTabs: ['rules', 'model', 'demo'],
    ticketActions: {
      create: false,
      assign: false,
      claim: true,
      start: true,
      resolve: true,
      close: false,
      cancel: false,
    },
  },
  SYSTEM_ADMIN: {
    label: '系统管理员',
    defaultRoute: '/admin',
    accessibleRoutes: ['/admin', '/agent', '/data', '/topology'],
    showAlertBadge: false,
    adminTabs: ['users', 'logs', 'health', 'rules', 'model'],
    ticketActions: {
      create: true,
      assign: true,
      claim: true,
      start: true,
      resolve: true,
      close: true,
      cancel: true,
    },
  },
}

/* ─── 菜单项配置（按角色） ─── */
export type NavMenuItem = Required<MenuProps>['items'][number]

export function getMenuItems(role: Role | undefined): NavMenuItem[] {
  if (!role) return []

  const items: NavMenuItem[] = []

  // 可视化大屏 — DISPATCHER, OPERATOR
  if (role === 'DISPATCHER' || role === 'OPERATOR') {
    items.push({
      key: '/dashboard',
      icon: <DashboardOutlined />,
      label: '运行大屏',
    })
  }

  // 告警/工单 — DISPATCHER, OPERATOR (不同名称)
  if (role === 'DISPATCHER' || role === 'OPERATOR') {
    items.push({
      key: '/alerts',
      icon: role === 'DISPATCHER' ? <AlertOutlined /> : <FileTextOutlined />,
      label: role === 'DISPATCHER' ? '告警中心' : '工单处置',
    })
  }

  // 智能助手 — 全部
  items.push({
    key: '/agent',
    icon: <RobotOutlined />,
    label: '智能助手',
  })

  // 数据查询 — 全部
  items.push({
    key: '/data',
    icon: <DatabaseOutlined />,
    label: '数据查询',
  })

  // 拓扑风险 — 全部角色
  items.push({
    key: '/topology',
    icon: <ApartmentOutlined />,
    label: '拓扑风险',
  })

  // 系统管理 — OPERATOR, SYSTEM_ADMIN
  if (role === 'OPERATOR' || role === 'SYSTEM_ADMIN') {
    items.push({
      key: '/admin',
      icon: <SettingOutlined />,
      label: role === 'OPERATOR' ? '运维管理' : '系统管理',
    })
  }

  return items
}

/* ─── Admin Tab 配置 ─── */
export interface AdminTab {
  key: string
  label: string
  icon: React.ReactNode
  roles: Role[]
}

export const ADMIN_TABS: AdminTab[] = [
  { key: 'users',  label: '用户管理', icon: <UserOutlined />,       roles: ['SYSTEM_ADMIN'] },
  { key: 'logs',   label: '操作日志', icon: <AuditOutlined />,       roles: ['SYSTEM_ADMIN'] },
  { key: 'health', label: '系统健康', icon: <HeartOutlined />,       roles: ['SYSTEM_ADMIN'] },
  { key: 'rules',  label: '告警规则', icon: <AlertOutlined />,       roles: ['OPERATOR', 'SYSTEM_ADMIN'] },
  { key: 'model',  label: '模型管理', icon: <RobotOutlined />,       roles: ['OPERATOR', 'SYSTEM_ADMIN'] },
  { key: 'demo',   label: '演示控制', icon: <ThunderboltOutlined />, roles: ['OPERATOR'] },
]

/** 获取当前角色可见的 Admin Tabs */
export function getAdminTabs(role: Role | undefined): AdminTab[] {
  if (!role) return []
  return ADMIN_TABS.filter((t) => t.roles.includes(role))
}

/* ─── 角色标签映射（用于展示） ─── */
export const ROLE_LABELS: Record<string, string> = {
  DISPATCHER: '电力调度员',
  OPERATOR: '运维管理员',
  SYSTEM_ADMIN: '系统管理员',
}
