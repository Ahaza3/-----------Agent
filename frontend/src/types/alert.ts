/**
 * 告警类型 — 对应后端 alert_event / alert_rule 表
 */

export type AlertLevel = 'RED' | 'ORANGE' | 'YELLOW'
export type AlertType = 'THRESHOLD' | 'TREND' | 'ANOMALY'

/** 告警事件 */
export interface AlertEvent {
  id: number
  triggerTime: string
  level: AlertLevel
  type: AlertType
  currentValue: number
  thresholdValue: number
  ruleId: number
  aiAnalysis: string
  suggestion: string
  isRead: number
  resolvedAt: string | null
  createdAt: string
}

/** 告警事件分页响应 */
export interface AlertPageResult {
  records: AlertEvent[]
  total: number
  page: number
  size: number
  pages: number
}

/** 告警规则 */
export interface AlertRule {
  id: number
  name: string
  type: string
  config: string
  isActive: number
  createdAt: string
  updatedAt: string
}

/** WebSocket 实时负荷推送（RealtimeLoadPoint） */
export interface WsLoadPayload {
  type: 'load_update'
  data: {
    timestamp: number
    sequence: number
    loadMw: number
    temperature: number | null
    humidity: number | null
    source: string
  }
}

/** WebSocket 告警推送 */
export interface WsAlertPayload {
  type: 'alert'
  data: {
    id: number
    triggerTime: string
    level: AlertLevel
    currentValue: number
    thresholdValue: number
    aiAnalysis: string
    suggestion: string
  }
}

/** WebSocket 预测推送 */
export interface WsPredictionPayload {
  type: 'prediction_update'
  data: {
    predictions: number[]
    model: string
    forecastStartTime: string | null
  }
}

/** 前端展示配置 */
export const ALERT_LEVEL_CONFIG: Record<AlertLevel, { label: string; color: string; bg: string }> = {
  RED:    { label: '紧急', color: '#FF2A2A', bg: 'rgba(255,42,42,0.12)' },
  ORANGE: { label: '重要', color: '#FA8C16', bg: 'rgba(250,140,22,0.12)' },
  YELLOW: { label: '提示', color: '#FADB14', bg: 'rgba(250,219,20,0.10)' },
}
