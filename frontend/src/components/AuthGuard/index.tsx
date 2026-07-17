import { Navigate, Outlet, useLocation } from 'react-router-dom'
import useAuthStore from '../../stores/useAuthStore'

/** 路由守卫：未登录重定向到 /login，登录后放行子路由 */
const AuthGuard = () => {
  const location = useLocation()
  const token = useAuthStore((s) => s.accessToken)

  if (!token) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }

  return <Outlet />
}

export default AuthGuard
