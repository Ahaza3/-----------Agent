/**
 * 统一工单/告警详情 — 桌面右侧 Drawer，移动端全屏 Drawer
 * 展示：告警信息、阈值、AI 分析、工单状态、时间线、操作栏
 */
import { useState, useEffect, useCallback } from 'react'
import { Drawer, Descriptions, Tag, Spin, message, Empty, Button, Modal, Input } from 'antd'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CopyOutlined,
  FileTextOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import { ALERT_LEVEL_CONFIG } from '../../../types/alert'
import type { AlertLevel } from '../../../types/alert'
import type { Role } from '../../../config/roles'
import * as ticketApi from '../../../services/ticketApi'
import type { Ticket } from '../../../services/ticketApi'
import TicketTimeline from './TicketTimeline'
import TicketActionBar from './TicketActionBar'
import type { TimelineEntry } from './TicketTimeline'
import { acknowledgeAlert } from '../../../services/alertApi'
import './AlertTicketDetail.css'

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
  type?: string
  level: AlertLevel
  currentValue: number
  thresholdValue: number
  triggerTime: string
  status?: 'ACTIVE' | 'ACKNOWLEDGED' | 'RECOVERED'
  acknowledgedAt?: string | null
  acknowledgedByName?: string | null
  aiAnalysis?: string
  suggestion?: string
}

/** 工单报告生成器 */
const ReportGenerator = ({ ticketId, currentResolution }: { ticketId: number; currentResolution?: string }) => {
  const [open, setOpen] = useState(false)
  const [note, setNote] = useState('')
  const [report, setReport] = useState('')
  const [loading, setLoading] = useState(false)

  const handleGenerate = async () => {
    setLoading(true)
    try {
      const r: any = await ticketApi.generateTicketReport(ticketId, note)
      setReport(r.report || '')
      message.success('报告已生成')
    } catch { message.error('报告生成失败') }
    finally { setLoading(false) }
  }

  return (
    <>
      <Button size="small" icon={<FileTextOutlined />} onClick={() => setOpen(true)} style={{ marginTop: 8 }}>
        AI 生成处理报告
      </Button>
      <Modal
        title="AI 生成处理报告"
        open={open}
        onCancel={() => setOpen(false)}
        footer={null}
        width={640}
      >
        <div style={{ marginBottom: 12 }}>
          <Input.TextArea
            rows={4}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="请输入实际处理过程（可选，留空将显示'待补充'）"
          />
        </div>
        <Button type="primary" loading={loading} onClick={handleGenerate} style={{ marginBottom: 12 }}>
          生成报告
        </Button>
        {report && (
          <>
            <pre style={{
              background: '#0a0a0a', border: '1px solid #2A2A2A', padding: 16,
              color: '#ccc', fontSize: 11, whiteSpace: 'pre-wrap', maxHeight: 400, overflow: 'auto',
            }}>{report}</pre>
            <div style={{ marginTop: 8, display: 'flex', gap: 8 }}>
              <Button size="small" icon={<CopyOutlined />} onClick={() => { navigator.clipboard.writeText(report); message.success('已复制') }}>
                复制报告
              </Button>
              {currentResolution === undefined && (
                <Button size="small" onClick={() => { setNote(report); message.info('已将报告填入处理结果框'); setOpen(false) }}>
                  填入处理结果
                </Button>
              )}
            </div>
          </>
        )}
      </Modal>
    </>
  )
}

interface Props {
  open: boolean
  onClose: () => void
  role: Role | undefined
  userId: number | undefined
  alert: AlertInfo
  ticket: Ticket | null
  onTicketUpdated: (ticket: Ticket) => void
  onAlertAcknowledged?: (alert: { id: number; status: 'ACKNOWLEDGED'; acknowledgedAt: string; acknowledgedByName?: string | null }) => void
  onAssign: () => void
  onResolve: () => void
  /** 是否全屏（移动端） */
  fullscreen?: boolean
}

const AlertTicketDetail = ({
  open, onClose, role, userId, alert, ticket: initialTicket,
  onTicketUpdated, onAssign, onResolve, fullscreen,
  onAlertAcknowledged,
}: Props) => {
  const [ticket, setTicket] = useState<Ticket | null>(initialTicket)
  const [actions, setActions] = useState<TimelineEntry[]>([])
  const [loading, setLoading] = useState(false)
  const ticketId = ticket?.id

  // 同步外部 ticket 变化
  useEffect(() => {
    setTicket(initialTicket)
    setActions([])
  }, [initialTicket])

  // 加载工单操作时间线
  useEffect(() => {
    if (!ticketId || !open) {
      setActions([])
      return
    }
    const load = async () => {
      try {
        setActions([])
        const acts = await ticketApi.fetchTicketActions(ticketId)
        setActions(acts as TimelineEntry[])
      } catch { setActions([]) }
    }
    load()
  }, [ticketId, open])

  // 加载智能研判
  const [judgement, setJudgement] = useState<any>(null)
  const [judgementLoading, setJudgementLoading] = useState(false)
  useEffect(() => {
    if (!alert?.id || !open) { setJudgement(null); return }
    setJudgementLoading(true)
    ticketApi.fetchJudgement(alert.id)
      .then(setJudgement)
      .catch(() => setJudgement(null))
      .finally(() => setJudgementLoading(false))
  }, [alert?.id, open])

  const handleRejudge = async () => {
    if (!alert?.id) return
    setJudgementLoading(true)
    try { setJudgement(await ticketApi.rejudge(alert.id)); message.success('研判已更新') }
    catch { message.error('重新研判失败') }
    finally { setJudgementLoading(false) }
  }

  const handleAcknowledge = async () => {
    if (!alert.id) return
    setLoading(true)
    try {
      await acknowledgeAlert(alert.id)
      const updated = {
        id: alert.id,
        status: 'ACKNOWLEDGED' as const,
        acknowledgedAt: new Date().toISOString(),
        acknowledgedByName: null,
      }
      onAlertAcknowledged?.(updated)
      message.success('告警已确认')
    } catch (e: any) {
      message.error(e?.response?.data?.message || e.message || '告警确认失败')
    } finally {
      setLoading(false)
    }
  }

  const st = ticket ? STATUS[ticket.status] || { label: ticket.status, color: 'default' } : null
  const pr = ticket ? PRIORITY[ticket.priority] || { label: ticket.priority, color: 'default' } : null
  const alertLevelCfg = ALERT_LEVEL_CONFIG[alert.level]
  const limitLabel = alert.type === 'TOPOLOGY_RISK' ? '节点容量' : '阈值'

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
      className="alert-detail-drawer"
      styles={{
        body: { padding: 16 },
        header: { borderBottom: '1px solid #2A2A2A' },
      }}
    >
      <Spin spinning={loading}>
        <div className="alert-detail-content">

        <section className={`alert-detail-hero alert-detail-hero--${alert.level.toLowerCase()}`}>
          <div className="alert-detail-hero__topline">
            <span>{alert.type === 'TOPOLOGY_RISK' ? 'TOPOLOGY RISK' : 'LOAD ALERT'}</span>
            <Tag color={alertLevelCfg?.color}>{alertLevelCfg?.label || alert.level}</Tag>
          </div>
          <div className="alert-detail-hero__main">
            <div>
              <h2>{alert.type === 'TOPOLOGY_RISK' ? '拓扑节点风险' : '负荷运行告警'}</h2>
              <p>
                {ticket?.sourceType === 'PREWARNING'
                  ? '基于预测结果生成的提前处置线索'
                  : '需要结合实时负荷、预测峰值和容量余量共同判断'}
              </p>
            </div>
            <span className="alert-detail-hero__status">
              {alert.status === 'RECOVERED' ? '已恢复' : alert.status === 'ACKNOWLEDGED' ? '已确认' : '待确认'}
            </span>
          </div>
        </section>

        <section className="alert-detail-section">
          <div className="alert-detail-section__heading">
            <span className="alert-detail-section__index">01</span>
            <div>
              <h3>运行证据</h3>
              <p>规则判断使用的关键数据</p>
            </div>
          </div>
          <div className="alert-detail-metrics">
            <div className="alert-detail-metric">
              <span>{ticket?.sourceType === 'PREWARNING' ? '预测负荷' : '当前负荷'}</span>
              <strong>{`${alert.currentValue?.toFixed(1)} MW`}</strong>
              <small>实时信号</small>
            </div>
            <div className="alert-detail-metric">
              <span>{limitLabel}</span>
              <strong>{`${alert.thresholdValue?.toFixed(0)} MW`}</strong>
              <small>{alert.type === 'TOPOLOGY_RISK' ? '节点额定能力' : '规则基准值'}</small>
            </div>
            <div className="alert-detail-metric">
              <span>预测峰值</span>
              <strong>{judgement?.forecastPeakLoad ? `${Number(judgement.forecastPeakLoad).toFixed(0)} MW` : '--'}</strong>
              <small>{judgement?.forecastPeakLoad ? '未来 24 小时' : '暂无可用研判'}</small>
            </div>
          </div>
          <div className="alert-detail-meta">
            <span><ClockCircleOutlined /> {ticket?.sourceType === 'PREWARNING' ? '预测时间' : '触发时间'}：{alert.triggerTime ? dayjs(alert.triggerTime).format('MM-DD HH:mm:ss') : '-'}</span>
            {alert.acknowledgedAt && <span><CheckCircleOutlined /> {alert.acknowledgedByName || '调度员'} 已确认</span>}
          </div>
        </section>
        {role === 'DISPATCHER' && alert.status !== 'ACKNOWLEDGED' && alert.status !== 'RECOVERED' && (
          <Button className="alert-detail-confirm" type="primary" size="small" loading={loading} onClick={handleAcknowledge}>
            确认告警
          </Button>
        )}

        {/* 智能研判 */}
        {judgement && (
          <section className="alert-detail-section alert-judgement-section">
            <div className="alert-judgement-header">
              <div className="alert-detail-section__heading">
                <span className="alert-detail-section__index">02</span>
                <div>
                  <h3>AI 智能研判</h3>
                  <p>规则负责计算，模型负责解释与建议</p>
                </div>
              </div>
              <Button size="small" icon={<ReloadOutlined />} loading={judgementLoading} onClick={handleRejudge}>重新研判</Button>
            </div>
            <Descriptions className="alert-judgement-facts" size="small" column={1}>
              <Descriptions.Item label="建单建议">
                {judgement.shouldCreateTicket
                  ? <Tag color="green">建议提交待确认工单草稿</Tag>
                  : <Tag color="default">暂不建议建单</Tag>
                }
              </Descriptions.Item>
              <Descriptions.Item label="推荐优先级">
                <Tag color={judgement.recommendedPriority === 'URGENT' ? 'red' : judgement.recommendedPriority === 'HIGH' ? 'orange' : 'default'}>
                  {judgement.recommendedPriority === 'URGENT' ? '紧急' : judgement.recommendedPriority === 'HIGH' ? '高' : '普通'}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="趋势方向">
                {judgement.trendDirection === 'RISING' ? '↑ 上升' : judgement.trendDirection === 'FALLING' ? '↓ 下降' : judgement.trendDirection === 'STABLE' ? '→ 平稳' : '未知'}
              </Descriptions.Item>
              {judgement.forecastPeakLoad != null && judgement.forecastPeakLoad > 0 && (
                <Descriptions.Item label="预测峰值">
                    <span className={judgement.currentLoad && judgement.forecastPeakLoad < judgement.currentLoad * 0.9 ? 'is-stale' : ''}>
                    {`${judgement.forecastPeakLoad.toFixed(0)} MW`}
                    {judgement.currentLoad && judgement.forecastPeakLoad < judgement.currentLoad * 0.9 ? '（预测数据可能过期）' : ''}
                  </span>
                </Descriptions.Item>
              )}
            </Descriptions>
            <div className="alert-judgement-callout alert-judgement-callout--reason">
              <div className="alert-judgement-callout__label">研判结论</div>
              <div>{judgement.decisionReason}</div>
            </div>
            <div className="alert-judgement-callout alert-judgement-callout--dispatch">
              <div className="alert-judgement-callout__label">调度员建议</div>
              <div>{judgement.dispatcherAdvice}</div>
            </div>
            <div className="alert-judgement-callout alert-judgement-callout--operator">
              <div className="alert-judgement-callout__label">运维建议</div>
              <div>{judgement.operatorAdvice}</div>
            </div>
            <Descriptions className="alert-judgement-routing" size="small" column={1}>
              <Descriptions.Item label="拓扑责任路由">
                <Tag color={judgement.routingTarget === 'SUBSTATION_OPERATOR' ? 'blue' : 'gold'}>
                  {judgement.routingTarget === 'SUBSTATION_OPERATOR' ? '变电站运维' : '调度中心'}
                </Tag>
                {judgement.recommendedAssigneeName
                  ? ` ${judgement.recommendedAssigneeName}`
                  : ' 待调度中心认领'}
              </Descriptions.Item>
              {judgement.ticketTitle && (
                <Descriptions.Item label="工单草稿标题">{judgement.ticketTitle}</Descriptions.Item>
              )}
              {judgement.impactScope?.length > 0 && (
                <Descriptions.Item label="影响范围">
                  {judgement.impactScope.join('、')}
                </Descriptions.Item>
              )}
              {judgement.rootCauseHints?.length > 0 && (
                <Descriptions.Item label="候选根因">
                  {judgement.rootCauseHints.join('；')}
                </Descriptions.Item>
              )}
            </Descriptions>
            <div className="alert-judgement-source">
              <Tag color="default">来源：规则型智能 Agent</Tag>
            </div>
          </section>
        )}
        {!judgement && !judgementLoading && (
          <div style={{ marginBottom: 12, border: '1px solid #2A2A2A', padding: 12, background: '#0c0c0c', textAlign: 'center' }}>
            <div style={{ color: '#888', fontSize: 12, marginBottom: 8 }}>暂无缓存研判，请点击重新研判。</div>
            <Button size="small" loading={judgementLoading} onClick={handleRejudge}>重新研判</Button>
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

            {/* AI 生成处理报告 */}
            {(role === 'OPERATOR' || role === 'SYSTEM_ADMIN') && (
              <ReportGenerator ticketId={ticket.id} currentResolution={ticket.resolution} />
            )}
          </>
        )}

        {!ticket && (
          <Empty description="暂无关联工单" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}

        {/* 时间线 */}
        <h4 style={{ color: '#aaa', fontSize: 12, marginTop: 16, marginBottom: 8 }}>处置时间线</h4>
        <TicketTimeline actions={actions} />

        </div>
      </Spin>
    </Drawer>
  )
}

export default AlertTicketDetail
