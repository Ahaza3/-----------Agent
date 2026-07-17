import { Navigate, Outlet } from 'react-router-dom'
import useAuthStore from '../../stores/useAuthStore'

/** 认证守卫：未登录重定向到 /login，登录后放行子路由 */
const AuthGuard = () => {
  const token = useAuthStore((s) => s.accessToken)

  if (!token) {
    return <Navigate to="/login" replace />
  }

  return <Outlet />
}

export default AuthGuard
