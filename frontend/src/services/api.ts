/**
 * API 调用封装 — axios 实例 + 统一响应处理
 */
import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json; charset=utf-8',
  },
})

// 响应拦截器 — 统一提取 data 字段
api.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body.code !== 0) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  },
  (error) => {
    return Promise.reject(error)
  },
)

export default api
