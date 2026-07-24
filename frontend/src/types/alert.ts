/**
 * 告警类型 — 对应后端 alert_event / alert_rule 表
 */

export type AlertLevel = 'RED' | 'ORANGE' | 'YELLOW'
export type AlertType = 'THRESHOLD' | 'TREND' | 'ANOMALY' | 'TOPOLOGY_RISK'

/** 告警事件 */
export interface AlertEvent {
  id: number
  nodeId?: number | null
  rootEventId?: number | null
  impactLoadMw?: number | null
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
  status?: 'ACTIVE' | 'ACKNOWLEDGED' | 'RECOVERED'
  acknowledgedAt?: string | null
  acknowledgedByName?: string | null
  occurrenceNo?: number | null
  stateKey?: string | null
  ruleVersion?: string | null
  dataSource?: string | null
  sourceObservedAt?: string | null
  sourceReceivedAt?: string | null
  recoveredAt?: string | null
  topologyVersion?: string | null
  topologySimulated?: boolean | null
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
    nodeId?: number
    observedAt?: string | null
    receivedAt?: string | null
    sourceInstanceId?: string | null
    qualityCode?: 'GOOD' | 'ESTIMATED' | 'LATE' | 'BAD'
    qualityReason?: string | null
    dataSource?: string | null
    estimated?: boolean
    freshnessStatus?: 'FRESH' | 'STALE'
    persistenceStatus?: 'PERSISTED' | 'PERSISTENCE_DEGRADED'
  }
}

/** WebSocket 告警推送 */
export interface WsAlertPayload {
  type: 'alert'
  data: {
    id: number
    nodeId: number | null
    rootEventId: number | null
    impactLoadMw: number | null
    triggerTime: string
    level: AlertLevel
    type: AlertType
    currentValue: number
    thresholdValue: number
    aiAnalysis: string
    suggestion: string
    occurrenceNo?: number | null
    ruleVersion?: string | null
    dataSource?: string | null
    sourceObservedAt?: string | null
    qualityCode?: string | null
    topologyVersion?: string | null
    topologySimulated?: boolean | null
  }
}

/** WebSocket 预测推送 */
export interface WsPredictionPayload {
  type: 'prediction_update'
  data: {
    nodeId?: number | null
    source?: string
    predictions: number[]
    model: string
    forecastStartTime: string | null
    lowerBounds?: number[] | null
    upperBounds?: number[] | null
    intervalSource?: string | null
    modelVersionId?: number | null
    futureWeatherAvailable?: boolean
    weatherSource?: string | null
    futureWeatherApplied?: boolean
    futureWeatherFallback?: boolean
  }
}

/** 前端展示配置 */
export const ALERT_LEVEL_CONFIG: Record<AlertLevel, { label: string; color: string; bg: string }> = {
  RED:    { label: '紧急', color: '#FF2A2A', bg: 'rgba(255,42,42,0.12)' },
  ORANGE: { label: '重要', color: '#FA8C16', bg: 'rgba(250,140,22,0.12)' },
  YELLOW: { label: '提示', color: '#FADB14', bg: 'rgba(250,219,20,0.10)' },
}
