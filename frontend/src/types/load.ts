/**
 * 负荷数据类型
 * 对应后端 load_data 表
 */
export interface LoadData {
  /** 主键 */
  id: number
  /** 数据时间点 (ISO 8601) */
  time: string
  /** 负荷值 (MW) */
  loadValue: number
  /** 变电站名称 */
  substation?: string
  /** 所属区域 */
  region?: string
  /** 数据来源: meter = 电表采集, scada = SCADA, mock = 模拟 */
  source?: 'meter' | 'scada' | 'mock'
  /** 创建时间 */
  createTime: string
}

/**
 * 负荷查询参数
 */
export interface LoadQuery {
  /** 开始时间 */
  startTime?: string
  /** 结束时间 */
  endTime?: string
  /** 变电站筛选 */
  substation?: string
  /** 区域筛选 */
  region?: string
}
