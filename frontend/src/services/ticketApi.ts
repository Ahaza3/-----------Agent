/**
 * 工单 API — 对应 /api/v1/tickets/ 和 /api/v1/alerts/
 */
import api from './api'

export interface AssigneeInfo {
  id: number
  displayName: string
  username: string
  active: boolean
}

export interface Ticket {
  id: number
  ticketNo: string
  alertId: number | null
  sourceType?: 'ALERT' | 'PREWARNING'
  riskLevel?: string | null
  forecastTime?: string | null
  expectedLoad?: number | null
  status: string
  priority: string
  summary: string
  resolution: string | null
  createdBy: number
  createdByName: string
  assigneeUserId: number | null
  assigneeName: string | null
  assignedAt: string | null
  startedAt: string | null
  resolvedAt: string | null
  closedAt: string | null
  cancelledAt: string | null
  createdAt: string
  updatedAt: string
  responseDeadline?: string | null
  processingDeadline?: string | null
  slaStatus?: 'ON_TRACK' | 'OVERDUE_RESPONSE' | 'OVERDUE_PROCESSING' | 'COMPLETED'
}

export interface TicketAction {
  id: number
  ticketId: number
  action: string
  fromStatus: string | null
  toStatus: string
  operatorId: number
  operatorName: string
  operatorRole: string
  note: string | null
  createdAt: string
}

export interface TicketListParams {
  status?: string
  priority?: string
  assigneeUserId?: number
  alertLevel?: string
  keyword?: string
  page?: number
  size?: number
}

/* ─── 可指派运维人员 ─── */
export function fetchAssignees(): Promise<AssigneeInfo[]> {
  return api.get('/tickets/assignees')
}

/* ─── 工单列表 ─── */
export function fetchTickets(params: TicketListParams = {}): Promise<{
  records: Ticket[]
  total: number
  page: number
}> {
  return api.get('/tickets', { params })
}

/* ─── 工单详情 ─── */
export function fetchTicketDetail(id: number): Promise<Ticket> {
  return api.get(`/tickets/${id}`)
}

/* ─── 工单操作记录 ─── */
export function fetchTicketActions(id: number): Promise<TicketAction[]> {
  return api.get(`/tickets/${id}/actions`)
}

/* ─── 通过告警 ID 获取工单 ─── */
export function fetchTicketByAlert(alertId: number): Promise<Ticket> {
  return api.get(`/alerts/${alertId}/ticket`)
}

/* ─── 创建工单 ─── */
export function createTicket(alertId: number, summary: string): Promise<Ticket> {
  return api.post(`/alerts/${alertId}/ticket`, { summary })
}

export function createPrewarningTicket(payload: {
  summary: string
  riskLevel: string
  forecastTime: string
  expectedLoad: number
}): Promise<Ticket> {
  return api.post('/tickets/prewarning', payload)
}

/* ─── 指派人 ─── */
export function assignTicket(id: number, assigneeUserId: number): Promise<Ticket> {
  return api.put(`/tickets/${id}/assign`, { assigneeUserId })
}

/* ─── 认领 ─── */
export function claimTicket(id: number): Promise<Ticket> {
  return api.put(`/tickets/${id}/claim`)
}

/* ─── 开始处理 ─── */
export function startTicket(id: number): Promise<Ticket> {
  return api.put(`/tickets/${id}/start`)
}

/* ─── 标记解决 ─── */
export function resolveTicket(id: number, resolution: string): Promise<Ticket> {
  return api.put(`/tickets/${id}/resolve`, { resolution })
}

/* ─── 关闭 ─── */
export function closeTicket(id: number): Promise<Ticket> {
  return api.put(`/tickets/${id}/close`)
}

/* ─── 取消 ─── */
export function cancelTicket(id: number, reason?: string): Promise<Ticket> {
  return api.put(`/tickets/${id}/cancel`, { reason })
}

/* ─── 生成处理报告 ─── */
export function generateTicketReport(id: number, operatorNote?: string): Promise<{
  ticketId: number; ticketNo: string; report: string; source: string; generatedAt: string
}> {
  return api.post(`/tickets/${id}/report`, { operatorNote })
}

/* ─── 查询智能研判 ─── */
export interface JudgementResult {
  alertId: number
  level: string
  currentLoad: number
  thresholdValue: number
  trendDirection: string
  forecastPeakLoad: number | null
  forecastPeakTime: string | null
  hasExistingTicket: boolean
  hasOpenSimilarTicket: boolean
  shouldCreateTicket: boolean
  autoCreateTicket: boolean
  recommendedPriority: string
  dispatcherAdvice: string
  operatorAdvice: string
  decisionReason: string
  source: string
  createdAt: string
}

export function fetchJudgement(alertId: number): Promise<JudgementResult | null> {
  return api.get(`/alert/events/${alertId}/judgement`)
}

export function rejudge(alertId: number): Promise<JudgementResult> {
  return api.post(`/alert/events/${alertId}/judgement`)
}
