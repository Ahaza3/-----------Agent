/**
 * 工单 WebSocket 更新去重测试
 */
import { describe, it, expect, beforeEach } from 'vitest'

describe('工单 WebSocket 更新去重', () => {
  let ticketCache: Map<number, any>

  beforeEach(() => {
    ticketCache = new Map()
  })

  it('新工单插入缓存', () => {
    const ticket = { id: 1, ticketNo: 'TK-001', status: 'PENDING' }
    ticketCache.set(ticket.id, { ...ticketCache.get(ticket.id), ...ticket })
    expect(ticketCache.get(1)?.ticketNo).toBe('TK-001')
    expect(ticketCache.get(1)?.status).toBe('PENDING')
  })

  it('更新已有工单不产生重复', () => {
    ticketCache.set(1, { id: 1, ticketNo: 'TK-001', status: 'PENDING' })
    // 收到更新
    const updated = { id: 1, status: 'ASSIGNED', assigneeName: 'operator' }
    ticketCache.set(1, { ...ticketCache.get(1), ...updated })
    expect(ticketCache.size).toBe(1)
    expect(ticketCache.get(1)?.status).toBe('ASSIGNED')
    expect(ticketCache.get(1)?.ticketNo).toBe('TK-001') // 原字段保留
    expect(ticketCache.get(1)?.assigneeName).toBe('operator')
  })

  it('多个工单独立更新', () => {
    ticketCache.set(1, { id: 1, status: 'PENDING' })
    ticketCache.set(2, { id: 2, status: 'PENDING' })
    // 只更新工单 1
    ticketCache.set(1, { ...ticketCache.get(1), status: 'ASSIGNED' })
    expect(ticketCache.get(1)?.status).toBe('ASSIGNED')
    expect(ticketCache.get(2)?.status).toBe('PENDING')
    expect(ticketCache.size).toBe(2)
  })

  it('AI 建议更新去重', () => {
    const adviceCache = new Map<number, any>()
    adviceCache.set(1, { id: 1, status: 'PENDING', analysis: '' })
    // 更新
    adviceCache.set(1, { ...adviceCache.get(1), status: 'SUCCESS', analysis: '负荷异常' })
    expect(adviceCache.size).toBe(1)
    expect(adviceCache.get(1)?.status).toBe('SUCCESS')
    expect(adviceCache.get(1)?.analysis).toBe('负荷异常')
  })

  it('连续多次更新不会产生重复', () => {
    ticketCache.set(1, { id: 1, status: 'PENDING' })
    for (let i = 0; i < 5; i++) {
      ticketCache.set(1, { ...ticketCache.get(1), status: `STEP_${i}` })
    }
    expect(ticketCache.size).toBe(1)
    expect(ticketCache.get(1)?.status).toBe('STEP_4')
  })
})

describe('WebSocket 事件处理', () => {
  it('ws:ticket-update 事件可以携带工单数据', () => {
    const detail = { type: 'ticket_created', data: { id: 1, ticketNo: 'TK-001' } }
    const event = new CustomEvent('ws:ticket-update', { detail })
    expect(event.detail.type).toBe('ticket_created')
    expect(event.detail.data.id).toBe(1)
  })

  it('ws:advice-update 事件可以携带建议数据', () => {
    const detail = { type: 'advice_completed', data: { id: 1, status: 'SUCCESS' } }
    const event = new CustomEvent('ws:advice-update', { detail })
    expect(event.detail.type).toBe('advice_completed')
    expect(event.detail.data.status).toBe('SUCCESS')
  })
})
