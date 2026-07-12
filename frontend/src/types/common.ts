/**
 * 工具函数和常量类型
 */

/** 时间粒度 */
export type Granularity = '1min' | '5min' | '15min' | '1hour' | '1day'

/** 仪表盘统计概览 */
export interface DashboardStats {
  /** 当前总负荷 (MW) */
  currentLoad: number
  /** 今日峰值负荷 (MW) */
  peakLoad: number
  /** 今日谷值负荷 (MW) */
  valleyLoad: number
  /** 负荷变化率 (%) */
  loadChangeRate: number
  /** 未处理告警数 */
  unacknowledgedAlerts: number
  /** 预测准确率 MAPE (%) */
  forecastAccuracy: number
}
