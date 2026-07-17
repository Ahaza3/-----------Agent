import { Navigate, Outlet } from 'react-router-dom'
import type { Role } from '../../config/roles'
import { PROTECTED_ROUTES } from '../../config/roles'
import useAuthStore from '../../stores/useAuthStore'

interface RoleGuardProps {
  /** 允许的角色列表；不传则只检查登录 */
  allowedRoles?: Role[]
  /** 路由匹配的 route key（用于自动获取 allowedRoles） */
  routeKey?: keyof typeof PROTECTED_ROUTES
}

/** 角色路由守卫：先检查认证，再检查角色权限 */
const RoleGuard = ({ allowedRoles, routeKey }: RoleGuardProps) => {
  const token = useAuthStore((s) => s.accessToken)
  const role = useAuthStore((s) => s.user?.role) as Role | undefined

  if (!token) {
    return <Navigate to="/login" replace />
  }

  const roles = allowedRoles ?? (routeKey ? PROTECTED_ROUTES[routeKey]?.allowedRoles : undefined)

  if (roles && role && !roles.includes(role)) {
    return <Navigate to="/403" replace />
  }

  return <Outlet />
}

export default RoleGuard
