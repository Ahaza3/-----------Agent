/**
 * 导航配置 — 从 roles.ts 派生，统一导航相关的工具函数
 */
import type { Role } from './roles'
import { ROLE_CONFIG, PROTECTED_ROUTES } from './roles'

/** 检查角色是否可访问某路由 */
export function canAccessRoute(role: Role | undefined, path: string): boolean {
  if (!role) return false
  // 匹配路由前缀（处理带参数的路由）
  for (const def of Object.values(PROTECTED_ROUTES)) {
    if (path === def.path || path.startsWith(def.path + '/')) {
      return def.allowedRoles.includes(role)
    }
  }
  // 不在配置中的路由（如 /login）默认允许
  return true
}

/** 获取角色默认首页路径 */
export function getDefaultRoute(role: Role | undefined): string {
  if (!role) return '/login'
  return ROLE_CONFIG[role]?.defaultRoute || '/login'
}

/** 获取角色可访问路由列表 */
export function getAccessibleRoutes(role: Role | undefined): string[] {
  if (!role) return []
  return ROLE_CONFIG[role]?.accessibleRoutes || []
}

/** 404 路由配置 */
export const NOT_FOUND_ROUTE = '/404'

/** 403 路由配置 */
export const FORBIDDEN_ROUTE = '/403'
