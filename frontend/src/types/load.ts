/**
 * 负荷数据类型
 * 对应后端 LoadData entity → load_data 表
 *
 * 字段名与后端 Jackson 序列化保持一致（MyBatis-Plus 下划线转驼峰）
 */
export interface LoadData {
  /** 主键 */
  id: number
  /** 数据时间点 (ISO 8601) */
  time: string
  /** 负荷值 (MW) — 对应后端字段 loadMw */
  loadMw: number
  /** 温度 (°C) */
  temperature: number
  /** 湿度 (%) */
  humidity: number
  /** 是否节假日: 0=否, 1=是 */
  isHoliday: 0 | 1
  /** 小时 0-23 */
  hour: number
  /** 星期 0=周一 */
  dayOfWeek: number
  /** 月份 1-12 */
  month: number
  /** 创建时间 */
  createdAt: string
}

/**
 * 负荷查询参数
 */
export interface LoadQuery {
  /** 开始时间 */
  startTime?: string
  /** 结束时间 */
  endTime?: string
  /** 变电站筛选（预留） */
  substation?: string
  /** 区域筛选（预留） */
  region?: string
}

/**
 * 负荷统计概览
 * 对应后端 LoadStats DTO
 */
export interface LoadStats {
  /** 峰值负荷 (MW) */
  peakLoad: number
  /** 峰值时间 */
  peakTime: string
  /** 谷值负荷 (MW) */
  valleyLoad: number
  /** 谷值时间 */
  valleyTime: string
  /** 平均负荷 (MW) */
  avgLoad: number
  /** 负荷率 (avg / peak) */
  loadRate: number
  /** 标准差 */
  stdDeviation: number
  /** 数据点数 */
  dataPoints: number
}
