import type { TicketFeedbackPayload } from '../services/ticketApi'

export function validateTicketFeedbackPayload(payload: TicketFeedbackPayload): string | null {
  if (!payload.alertClassification || !payload.rootCauseCode
    || !payload.actionsTaken.length || !payload.effectiveness) {
    return '请完整填写告警分类、根因、处置措施和处置效果'
  }
  if (payload.rootCauseCode === 'UNKNOWN' && !payload.rootCauseDetail?.trim()) {
    return '根因选择“未知”时必须填写根因说明'
  }
  if (payload.effectiveness === 'NOT_APPLICABLE' && !payload.actionDetail?.trim()) {
    return '处置效果为“不适用”时必须说明原因'
  }
  if (!Number.isFinite(payload.impactLoadMw) || payload.impactLoadMw < 0) {
    return '影响负荷必须为非负数'
  }
  if (payload.impactLoadMw === 0 && !payload.actionDetail?.includes('无影响')) {
    return '影响负荷为0时，请在处置说明中明确“无影响”'
  }
  if (payload.actionsTaken.some((action) => !action.trim() || action.trim().length > 200)) {
    return '每项处置措施不能为空且不超过200字'
  }
  return null
}
