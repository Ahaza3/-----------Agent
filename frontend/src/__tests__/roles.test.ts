/**
 * 角色导航矩阵测试 — 三角色菜单内容、默认首页
 */
import { describe, it, expect } from 'vitest'
import { ROLE_CONFIG, getMenuItems, getAdminTabs } from '../config/roles'
import { getDefaultRoute, canAccessRoute } from '../config/navigation'
import type { Role } from '../config/roles'

describe('角色配置 — ROLE_CONFIG', () => {
  it('DISPATCHER 默认首页为 /dashboard', () => {
    expect(getDefaultRoute('DISPATCHER')).toBe('/dashboard')
  })

  it('OPERATOR 默认首页为 /dashboard', () => {
    expect(getDefaultRoute('OPERATOR')).toBe('/dashboard')
  })

  it('SYSTEM_ADMIN 默认首页为 /admin', () => {
    expect(getDefaultRoute('SYSTEM_ADMIN')).toBe('/admin')
  })

  it('未登录角色默认首页为 /login', () => {
    expect(getDefaultRoute(undefined)).toBe('/login')
  })

  it('三个角色都有中文名', () => {
    const roles: Role[] = ['DISPATCHER', 'OPERATOR', 'SYSTEM_ADMIN']
    roles.forEach((r) => {
      expect(ROLE_CONFIG[r].label).toBeTruthy()
    })
  })
})

describe('菜单项 — getMenuItems', () => {
  it('DISPATCHER 菜单包含：运行大屏、告警中心、智能助手、数据查询', () => {
    const items = getMenuItems('DISPATCHER')
    const labels = items.map((i: any) => i.label)
    expect(labels).toContain('运行大屏')
    expect(labels).toContain('告警中心')
    expect(labels).toContain('智能助手')
    expect(labels).toContain('数据查询')
    // 不包含系统管理
    expect(labels).not.toContain('系统管理')
    expect(labels).not.toContain('运维管理')
  })

  it('OPERATOR 菜单包含：运行大屏、工单处置、智能助手、数据查询、运维管理', () => {
    const items = getMenuItems('OPERATOR')
    const labels = items.map((i: any) => i.label)
    expect(labels).toContain('运行大屏')
    expect(labels).toContain('工单处置')
    expect(labels).toContain('智能助手')
    expect(labels).toContain('数据查询')
    expect(labels).toContain('运维管理')
    expect(labels).not.toContain('告警中心')
    expect(labels).not.toContain('系统管理')
  })

  it('SYSTEM_ADMIN 菜单包含：智能助手、数据查询、系统管理', () => {
    const items = getMenuItems('SYSTEM_ADMIN')
    const labels = items.map((i: any) => i.label)
    expect(labels).toContain('智能助手')
    expect(labels).toContain('数据查询')
    expect(labels).toContain('系统管理')
    expect(labels).not.toContain('运行大屏')
    expect(labels).not.toContain('告警中心')
    expect(labels).not.toContain('工单处置')
  })
})

describe('Admin Tabs — getAdminTabs', () => {
  it('DISPATCHER 看不到任何 Admin Tab', () => {
    const tabs = getAdminTabs('DISPATCHER')
    expect(tabs).toHaveLength(0)
  })

  it('OPERATOR 可以看到 rules/model/demo', () => {
    const tabs = getAdminTabs('OPERATOR')
    const keys = tabs.map((t) => t.key)
    expect(keys).toContain('rules')
    expect(keys).toContain('model')
    expect(keys).toContain('demo')
    expect(keys).not.toContain('users')
    expect(keys).not.toContain('logs')
    expect(keys).not.toContain('health')
  })

  it('SYSTEM_ADMIN 可以看到 users/logs/health/rules/model 但不含 demo', () => {
    const tabs = getAdminTabs('SYSTEM_ADMIN')
    const keys = tabs.map((t) => t.key)
    expect(keys).toContain('users')
    expect(keys).toContain('logs')
    expect(keys).toContain('health')
    expect(keys).toContain('rules')
    expect(keys).toContain('model')
    expect(keys).not.toContain('demo')
  })
})

describe('路由权限 — canAccessRoute', () => {
  it('DISPATCHER 可访问 /dashboard, /alerts, /agent, /data', () => {
    expect(canAccessRoute('DISPATCHER', '/dashboard')).toBe(true)
    expect(canAccessRoute('DISPATCHER', '/alerts')).toBe(true)
    expect(canAccessRoute('DISPATCHER', '/agent')).toBe(true)
    expect(canAccessRoute('DISPATCHER', '/data')).toBe(true)
  })

  it('DISPATCHER 不可访问 /admin', () => {
    expect(canAccessRoute('DISPATCHER', '/admin')).toBe(false)
  })

  it('OPERATOR 可访问 /admin', () => {
    expect(canAccessRoute('OPERATOR', '/admin')).toBe(true)
  })

  it('SYSTEM_ADMIN 不可访问 /dashboard 和 /alerts', () => {
    expect(canAccessRoute('SYSTEM_ADMIN', '/dashboard')).toBe(false)
    expect(canAccessRoute('SYSTEM_ADMIN', '/alerts')).toBe(false)
  })

  it('SYSTEM_ADMIN 可访问 /admin, /agent, /data', () => {
    expect(canAccessRoute('SYSTEM_ADMIN', '/admin')).toBe(true)
    expect(canAccessRoute('SYSTEM_ADMIN', '/agent')).toBe(true)
    expect(canAccessRoute('SYSTEM_ADMIN', '/data')).toBe(true)
  })
})

describe('角色 ticket 权限', () => {
  it('DISPATCHER 可以创建/指派/关闭/取消，不能认领/处理/解决', () => {
    const a = ROLE_CONFIG.DISPATCHER.ticketActions
    expect(a.create).toBe(true)
    expect(a.assign).toBe(true)
    expect(a.close).toBe(true)
    expect(a.cancel).toBe(true)
    expect(a.claim).toBe(false)
    expect(a.start).toBe(false)
    expect(a.resolve).toBe(false)
  })

  it('OPERATOR 可以认领/处理/解决，不能创建/指派/关闭/取消', () => {
    const a = ROLE_CONFIG.OPERATOR.ticketActions
    expect(a.claim).toBe(true)
    expect(a.start).toBe(true)
    expect(a.resolve).toBe(true)
    expect(a.create).toBe(false)
    expect(a.assign).toBe(false)
    expect(a.close).toBe(false)
    expect(a.cancel).toBe(false)
  })

  it('SYSTEM_ADMIN 拥有全部工单权限', () => {
    const a = ROLE_CONFIG.SYSTEM_ADMIN.ticketActions
    Object.values(a).forEach((v) => expect(v).toBe(true))
  })
})

describe('ROLE_CONFIG 显示告警 Badge', () => {
  it('只有 DISPATCHER 显示告警 Badge', () => {
    expect(ROLE_CONFIG.DISPATCHER.showAlertBadge).toBe(true)
    expect(ROLE_CONFIG.OPERATOR.showAlertBadge).toBe(false)
    expect(ROLE_CONFIG.SYSTEM_ADMIN.showAlertBadge).toBe(false)
  })
})
