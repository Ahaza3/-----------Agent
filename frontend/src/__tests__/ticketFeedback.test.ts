import { describe, expect, it } from 'vitest'
import { validateTicketFeedbackPayload } from '../utils/ticketFeedback'
import type { TicketFeedbackPayload } from '../services/ticketApi'

const validPayload = (): TicketFeedbackPayload => ({
  alertClassification: 'TRUE_ALERT',
  rootCauseCode: 'EQUIPMENT',
  rootCauseDetail: '',
  actionsTaken: ['检查设备'],
  actionDetail: '无影响，设备状态正常',
  impactLoadMw: 0,
  effectiveness: 'EFFECTIVE',
})

describe('ticket feedback validation', () => {
  it('requires an unknown root cause detail', () => {
    const payload = validPayload()
    payload.rootCauseCode = 'UNKNOWN'
    expect(validateTicketFeedbackPayload(payload)).toContain('根因')
  })

  it('requires a reason for not applicable effectiveness', () => {
    const payload = validPayload()
    payload.effectiveness = 'NOT_APPLICABLE'
    payload.actionDetail = ''
    expect(validateTicketFeedbackPayload(payload)).toContain('不适用')
  })

  it('requires explicit no-impact meaning when MW is zero', () => {
    const payload = validPayload()
    payload.actionDetail = '已完成检查'
    expect(validateTicketFeedbackPayload(payload)).toContain('无影响')
  })

  it('rejects negative impact and oversized actions', () => {
    const payload = validPayload()
    payload.impactLoadMw = -1
    expect(validateTicketFeedbackPayload(payload)).toContain('非负')
    payload.impactLoadMw = 1
    payload.actionsTaken = ['x'.repeat(201)]
    expect(validateTicketFeedbackPayload(payload)).toContain('处置措施')
  })
})
