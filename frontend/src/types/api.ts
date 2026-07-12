/**
 * API 统一响应包装类型 R<T>
 * 对应后端 common/R<T>
 */
export interface R<T = unknown> {
  /** 业务状态码，0 = 成功 */
  code: number
  /** 提示信息 */
  message: string
  /** 响应数据 */
  data: T
  /** 时间戳 (ms) */
  timestamp: number
}

/**
 * 分页请求参数
 */
export interface PageQuery {
  page: number
  pageSize: number
}

/**
 * 分页响应
 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
