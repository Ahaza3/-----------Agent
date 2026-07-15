/**
 * 告警等级
 */
export type AlertLevel = 'info' | 'warning' | 'critical'

/**
 * 告警事件
 * 对应后端 alert_event 表
 */
export interface AlertEvent {
  /** 主键 */
  id: number
  /** 告警触发时间 (ISO 8601) */
  alertTime: string
  /** 告警等级 */
  level: AlertLevel
  /** 告警标题 */
  title: string
  /** 告警详情文案 */
  message: string
  /** 当前实际值 */
  currentValue: number
  /** 触发阈值 */
  thresholdValue: number
  /** 关联变电站 */
  substation?: string
  /** 是否已确认/已读 */
  acknowledged: boolean
  /** 确认人 */
  acknowledgedBy?: string
  /** 确认时间 */
  acknowledgedAt?: string
  /** 创建时间 */
  createTime: string
}

/**
 * 告警规则
 * 对应后端 alert_rule 表
 */
export interface AlertRule {
  /** 主键 */
  id: number
  /** 规则名称 */
  name: string
  /** 监控指标 */
  metric: string
  /** 比较运算符 */
  operator: 'gt' | 'lt' | 'gte' | 'lte'
  /** 阈值 */
  threshold: number
  /** 触发等级 */
  level: AlertLevel
  /** 是否启用 */
  enabled: boolean
  /** 创建时间 */
  createTime: string
  /** 更新时间 */
  updateTime: string
}

/**
 * 告警等级配置（前端展示用）
 */
export const ALERT_LEVEL_CONFIG: Record<AlertLevel, { label: string; color: string }> = {
  info: { label: '提示', color: '#60a5fa' },
  warning: { label: '预警', color: '#fbbf24' },
  critical: { label: '严重', color: '#f87171' },
}
