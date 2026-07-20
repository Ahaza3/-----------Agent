/**
 * API 调用封装 — axios 实例 + JWT 拦截器 + 统一响应处理
 */
import axios from 'axios'
import useAuthStore from '../stores/useAuthStore'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json; charset=utf-8' },
})

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let isRefreshing = false
let failedQueue: Array<{ resolve: (v: any) => void; reject: (e: any) => void }> = []

function processQueue(error: any, token: string | null = null) {
  failedQueue.forEach((p) => { if (token) p.resolve(token); else p.reject(error) })
  failedQueue = []
}

api.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body.code !== 0) return Promise.reject(new Error(body.message || '请求失败'))
    return body.data
  },
  async (error) => {
    const orig = error.config
    if (error.response?.status === 401 && orig && !orig._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then((token) => { orig.headers.Authorization = `Bearer ${token}`; return api(orig) })
      }
      orig._retry = true; isRefreshing = true
      const rt = useAuthStore.getState().refreshToken
      if (!rt) { useAuthStore.getState().logout(); window.location.href = '/login'; return Promise.reject(error) }
      try {
        const resp = await axios.post('/api/v1/auth/refresh', { refreshToken: rt })
        const data = resp.data.data
        useAuthStore.getState().setAuth(data.accessToken, data.refreshToken, data.user)
        processQueue(null, data.accessToken)
        orig.headers.Authorization = `Bearer ${data.accessToken}`
        return api(orig)
      } catch (e) {
        processQueue(e, null)
        useAuthStore.getState().logout()
        window.location.href = '/login'
        return Promise.reject(e)
      } finally { isRefreshing = false }
    }
    return Promise.reject(error)
  },
)

export default api
