/**
 * 统一工单/告警详情 — 桌面右侧 Drawer，移动端全屏 Drawer
 * 展示：告警信息、阈值、AI 分析、工单状态、时间线、操作栏
 */
import { useState, useEffect, useCallback } from 'react'
import { Drawer, Descriptions, Tag, Spin, message, Empty } from 'antd'
import dayjs from 'dayjs'
import { ALERT_LEVEL_CONFIG } from '../../../types/alert'
import type { AlertLevel } from '../../../types/alert'
import type { Role } from '../../../config/roles'
import * as ticketApi from '../../../services/ticketApi'
import type { Ticket } from '../../../services/ticketApi'
import TicketTimeline from './TicketTimeline'
import TicketActionBar from './TicketActionBar'
import type { TimelineEntry } from './TicketTimeline'
import api from '../../../services/api'

const STATUS: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待处理', color: 'default' },
  ASSIGNED: { label: '已指派', color: 'blue' },
  IN_PROGRESS: { label: '处理中', color: 'processing' },
  RESOLVED: { label: '已解决', color: 'green' },
  CLOSED: { label: '已关闭', color: 'default' },
  CANCELLED: { label: '已取消', color: 'default' },
}

const PRIORITY: Record<string, { label: string; color: string }> = {
  URGENT: { label: '紧急', color: 'red' },
  HIGH: { label: '高', color: 'orange' },
  NORMAL: { label: '普通', color: 'default' },
}

interface AlertInfo {
  id: number | null
  level: AlertLevel
  currentValue: number
  thresholdValue: number
  triggerTime: string
  aiAnalysis?: string
  suggestion?: string
}

interface Props {
  open: boolean
  onClose: () => void
  role: Role | undefined
  userId: number | undefined
  alert: AlertInfo
  ticket: Ticket | null
  onTicketUpdated: (ticket: Ticket) => void
  onAssign: () => void
  onResolve: () => void
  /** 是否全屏（移动端） */
  fullscreen?: boolean
}

const AlertTicketDetail = ({
  open, onClose, role, userId, alert, ticket: initialTicket,
  onTicketUpdated, onAssign, onResolve, fullscreen,
}: Props) => {
  const [ticket, setTicket] = useState<Ticket | null>(initialTicket)
  const [actions, setActions] = useState<TimelineEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [advices, setAdvices] = useState<any[]>([])
  const ticketId = ticket?.id

  // 同步外部 ticket 变化
  useEffect(() => {
    setTicket(initialTicket)
  }, [initialTicket])

  // 加载工单操作时间线
  useEffect(() => {
    if (!ticketId || !open) return
    const load = async () => {
      try {
        const acts = await ticketApi.fetchTicketActions(ticketId)
        setActions(acts as TimelineEntry[])
      } catch { setActions([]) }
    }
    load()
  }, [ticketId, open])

  // 加载 AI 建议
  useEffect(() => {
    if (!alert?.id || !open) {
      setAdvices([])
      return
    }
    (api.get(`/alert/events/${alert.id}/advice`) as Promise<any[]>).then(setAdvices).catch(() => setAdvices([]))
  }, [alert?.id, open])

  const st = ticket ? STATUS[ticket.status] || { label: ticket.status, color: 'default' } : null
  const pr = ticket ? PRIORITY[ticket.priority] || { label: ticket.priority, color: 'default' } : null
  const alertLevelCfg = ALERT_LEVEL_CONFIG[alert.level]

  const handleClaim = useCallback(async () => {
    if (!ticket) return
    setLoading(true)
    try {
      const updated = await ticketApi.claimTicket(ticket.id)
      setTicket(updated)
      onTicketUpdated(updated)
      message.success('已认领工单')
    } catch (e: any) {
      if (e?.response?.status === 409) {
        message.error('工单已被其他用户更新，请刷新')
      } else {
        message.error(e?.response?.data?.message || '操作失败')
      }
    } finally { setLoading(false) }
  }, [ticket, onTicketUpdated])

  const handleStart = useCallback(async () => {
    if (!ticket) return
    setLoading(true)
    try {
      const updated = await ticketApi.startTicket(ticket.id)
      setTicket(updated)
      onTicketUpdated(updated)
      message.success('已开始处理')
    } catch (e: any) { message.error(e?.response?.data?.message || '操作失败') }
    finally { setLoading(false) }
  }, [ticket, onTicketUpdated])

  const handleClose = useCallback(async () => {
    if (!ticket) return
    setLoading(true)
    try {
      const updated = await ticketApi.closeTicket(ticket.id)
      setTicket(updated)
      onTicketUpdated(updated)
      message.success('工单已关闭')
    } catch (e: any) { message.error(e?.response?.data?.message || '操作失败') }
    finally { setLoading(false) }
  }, [ticket, onTicketUpdated])

  const handleCancel = useCallback(async () => {
    if (!ticket) return
    setLoading(true)
    try {
      const updated = await ticketApi.cancelTicket(ticket.id)
      setTicket(updated)
      onTicketUpdated(updated)
      message.success('工单已取消')
    } catch (e: any) { message.error(e?.response?.data?.message || '操作失败') }
    finally { setLoading(false) }
  }, [ticket, onTicketUpdated])

  const nextStep = (): string => {
    if (!ticket) return '等待创建工单'
    switch (ticket.status) {
      case 'PENDING': return '等待指派人或认领'
      case 'ASSIGNED': return ticket.assigneeUserId === userId ? '等待认领或开始处理' : '等待被指派人认领'
      case 'IN_PROGRESS': return ticket.assigneeUserId === userId ? '等待标记解决' : '等待处理人标记解决'
      case 'RESOLVED': return '等待调度员确认关闭'
      case 'CLOSED': return '已完结'
      case 'CANCELLED': return '已取消'
      default: return ticket.status
    }
  }

  return (
    <Drawer
      title={ticket ? `工单 ${ticket.ticketNo}` : '告警详情'}
      placement="right"
      width={fullscreen ? '100%' : 520}
      open={open}
      onClose={onClose}
      styles={{
        body: { padding: 16 },
        header: { borderBottom: '1px solid #2A2A2A' },
      }}
    >
      <Spin spinning={loading}>

        {/* 告警/预警信息 */}
        <Descriptions
          size="small" bordered column={1}
          style={{ marginBottom: 12 }}
          labelStyle={{ color: '#888', background: '#0c0c0c', width: 90 }}
          contentStyle={{ color: '#ccc', background: '#0e0e0e' }}
          items={[
            { label: ticket?.sourceType === 'PREWARNING' ? '预警级别' : '告警级别', children: alertLevelCfg ? <Tag color={alertLevelCfg.color}>{alertLevelCfg.label}</Tag> : alert.level },
            { label: ticket?.sourceType === 'PREWARNING' ? '预测负荷' : '当前负荷', children: `${alert.currentValue?.toFixed(1)} MW` },
            { label: '阈值', children: `${alert.thresholdValue?.toFixed(0)} MW` },
            { label: ticket?.sourceType === 'PREWARNING' ? '预测时间' : '触发时间', children: alert.triggerTime ? dayjs(alert.triggerTime).format('MM-DD HH:mm:ss') : '-' },
          ]}
        />

        {/* AI 建议 */}
        {advices.length > 0 && (
          <div style={{ marginBottom: 12 }}>
            <h4 style={{ color: '#aaa', fontSize: 12, marginBottom: 8 }}>AI 建议</h4>
            {advices.map((a: any) => (
              <div key={a.id} style={{ border: '1px solid #2A2A2A', padding: 8, marginBottom: 4, background: '#0c0c0c' }}>
                <Tag color={a.status === 'SUCCESS' ? 'green' : 'gold'} style={{ fontSize: 10, marginBottom: 4 }}>
                  {a.audienceRole === 'DISPATCHER' ? '调度员建议' : '运维建议'}
                </Tag>
                {a.analysis && <div style={{ color: '#ccc', fontSize: 11 }}>{a.analysis}</div>}
                {a.suggestion && <div style={{ color: '#aaa', fontSize: 11, marginTop: 4 }}>{a.suggestion}</div>}
              </div>
            ))}
          </div>
        )}

        {/* 工单信息 */}
        {ticket && (
          <>
            <Descriptions
              size="small" bordered column={1}
              style={{ marginBottom: 12 }}
              labelStyle={{ color: '#888', background: '#0c0c0c', width: 90 }}
              contentStyle={{ color: '#ccc', background: '#0e0e0e' }}
              items={[
                { label: '状态', children: st ? <Tag color={st.color}>{st.label}</Tag> : '--' },
                { label: '来源', children: ticket.sourceType === 'PREWARNING' ? <Tag color="gold">预测预警</Tag> : <Tag color="red">告警触发</Tag> },
                { label: '优先级', children: pr ? <Tag color={pr.color}>{pr.label}</Tag> : '--' },
                { label: '概要', children: ticket.summary || '--' },
                { label: '创建人', children: ticket.createdByName || '--' },
                { label: '处理人', children: ticket.assigneeName || '未分配' },
                { label: '下一步', children: <span style={{ color: '#FAAD14', fontSize: 11 }}>{nextStep()}</span> },
                { label: '创建时间', children: ticket.createdAt ? dayjs(ticket.createdAt).format('MM-DD HH:mm') : '-' },
                { label: '处理结果', children: ticket.resolution || '暂无' },
              ]}
            />

            {/* 操作栏 */}
            <TicketActionBar
              role={role}
              userId={userId}
              ticket={ticket}
              onAssign={onAssign}
              onClaim={handleClaim}
              onStart={handleStart}
              onResolve={onResolve}
              onClose={handleClose}
              onCancel={handleCancel}
            />
          </>
        )}

        {!ticket && (
          <Empty description="暂无关联工单" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}

        {/* 时间线 */}
        <h4 style={{ color: '#aaa', fontSize: 12, marginTop: 16, marginBottom: 8 }}>处置时间线</h4>
        <TicketTimeline actions={actions} />

      </Spin>
    </Drawer>
  )
}

export default AlertTicketDetail
