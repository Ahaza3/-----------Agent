/**
 * 告警/工单中心 — DISPATCHER 看告警，OPERATOR 看工单
 */
import { useState, useEffect, useCallback, useRef } from 'react'
import { Table, Tag, Button, Segmented, Space, message, Badge, Typography, Modal, Input, Timeline, Descriptions } from 'antd'
import { BellOutlined, FileTextOutlined, CheckOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { fetchAlertEvents, markAlertRead } from '../../services/alertApi'
import api from '../../services/api'
import { ALERT_LEVEL_CONFIG } from '../../types/alert'
import type { AlertEvent, AlertLevel } from '../../types/alert'
import type { ColumnsType } from 'antd/es/table'
import useDashboardStore from '../../stores/useDashboardStore'
import useAuthStore from '../../stores/useAuthStore'

type LevelFilter = AlertLevel | 'ALL'

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

/* ════════════════════ 调度员视图 — 告警列表 ════════════════════ */
const DispatcherAlerts = () => {
  const [events, setEvents] = useState<AlertEvent[]>([])
  const [levelFilter, setLevelFilter] = useState<LevelFilter>('ALL')
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [loading, setLoading] = useState(false)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const wsAlerts = useDashboardStore((s) => s.alerts)
  const acknowledgeStoreAlert = useDashboardStore((s) => s.acknowledgeAlert)
  const seenIds = useRef(new Set<number>())
  const [createOpen, setCreateOpen] = useState<number | null>(null)
  const [summary, setSummary] = useState('')
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set())
  const [adviceCache, setAdviceCache] = useState<Record<number, any[]>>({})

  useEffect(() => {
    if (wsAlerts.length === 0) return
    const fresh = wsAlerts.filter((a) => !seenIds.current.has(a.id))
    if (fresh.length === 0) return
    fresh.forEach((a) => seenIds.current.add(a.id))
    fresh.forEach((a) => { try { acknowledgeStoreAlert(a.id) } catch {} })
  }, [wsAlerts, acknowledgeStoreAlert])

  const fetch = useCallback(async (p = page) => {
    setLoading(true)
    try {
      const params: any = { page: p, size: 20 }
      if (levelFilter !== 'ALL') params.level = levelFilter
      if (unreadOnly) params.unreadOnly = true
      const res: any = await fetchAlertEvents(params)
      setEvents(res.records || [])
      setTotal(res.total || 0)
    } catch { message.error('告警加载失败') }
    finally { setLoading(false) }
  }, [levelFilter, unreadOnly, page])
  useEffect(() => { fetch() }, [fetch])

  const handleMarkRead = async (id: number) => {
    try { await markAlertRead(id); setEvents((prev) => prev.map((e) => e.id === id ? { ...e, isRead: 1 } : e)) }
    catch { message.error('操作失败') }
  }

  const loadAdvice = async (alertId: number) => {
    if (adviceCache[alertId]) return
    try { const d: any = await api.get(`/alert/events/${alertId}/advice`); setAdviceCache((prev) => ({ ...prev, [alertId]: d || [] })) }
    catch {}
  }

  const doCreateTicket = async () => {
    if (!createOpen) return
    try { await api.post(`/alerts/${createOpen}/ticket`, { summary }); message.success('工单已创建'); setCreateOpen(null); setSummary('') }
    catch (e: any) { message.error(e?.response?.data?.message || e.message) }
  }

  const alertColumns: ColumnsType<AlertEvent> = [
    { title: '级别', dataIndex: 'level', width: 80, render: (v: AlertLevel) => <Tag color={ALERT_LEVEL_CONFIG[v]?.color}>{ALERT_LEVEL_CONFIG[v]?.label}</Tag> },
    { title: '当前负荷', dataIndex: 'currentValue', width: 100, render: (v: number) => `${v?.toFixed(1)} MW` },
    { title: '阈值', dataIndex: 'thresholdValue', width: 90, render: (v: number) => `${v?.toFixed(0)} MW` },
    { title: '时间', dataIndex: 'triggerTime', width: 140, render: (v: string) => v ? dayjs(v).format('MM-DD HH:mm:ss') : '-' },
    { title: '已读', dataIndex: 'isRead', width: 60, render: (v: number) => v === 0 ? <Badge status="error" /> : <Badge status="default" /> },
    {
      title: '操作', key: 'actions', width: 160, render: (_: any, r: AlertEvent) => (
        <Space size="small">
          {r.isRead === 0 && <Button size="small" onClick={() => handleMarkRead(r.id)}>标记已读</Button>}
          <Button size="small" type="primary" icon={<FileTextOutlined />} onClick={() => { setCreateOpen(r.id); setSummary('') }}>发起处置</Button>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Segmented value={levelFilter} onChange={(v) => { setLevelFilter(v as LevelFilter); setPage(1) }}
          options={[
            { label: '全部', value: 'ALL' },
            { label: (<span style={{ color: '#FF2A2A' }}>紧急</span>), value: 'RED' },
            { label: (<span style={{ color: '#FA8C16' }}>重要</span>), value: 'ORANGE' },
            { label: (<span style={{ color: '#FADB14' }}>提示</span>), value: 'YELLOW' },
          ]} />
        <Button type={unreadOnly ? 'primary' : 'default'} size="small" onClick={() => setUnreadOnly(!unreadOnly)}>仅未读</Button>
      </Space>
      <Table rowKey="id" dataSource={events} columns={alertColumns} loading={loading} size="small"
        pagination={{ current: page, pageSize: 20, total, showSizeChanger: false, onChange: (p) => { setPage(p); fetch(p) } }}
        expandable={{
          expandedRowKeys: [...expandedRows],
          onExpand: (expanded, record) => {
            const next = new Set(expandedRows)
            if (expanded) next.add(record.id); else next.delete(record.id)
            setExpandedRows(next)
            if (expanded) loadAdvice(record.id)
          },
          expandedRowRender: (record) => (
            <div>
              <AlertAdviceCards advices={adviceCache[record.id] || []} />
            </div>
          ),
        }}
      />
      <Modal title="发起处置工单" open={createOpen !== null} onCancel={() => setCreateOpen(null)} onOk={doCreateTicket} okText="创建">
        <Input.TextArea rows={3} value={summary} onChange={(e) => setSummary(e.target.value)} placeholder="工单概要（自动关联当前告警）" />
      </Modal>
    </div>
  )
}

/* ════════════════════ AI 建议卡片 ════════════════════ */
const AlertAdviceCards = ({ advices }: { advices: any[] }) => {
  if (!advices.length) return <div style={{ color: '#666', padding: 12 }}>暂无 AI 建议</div>
  const statusTag = (s: string) => {
    const m: Record<string, any> = { PENDING: { color: 'default', text: '生成中' }, SUCCESS: { color: 'green', text: 'AI 已生成' }, FALLBACK: { color: 'gold', text: '降级建议' } }
    const i = m[s] || { color: 'default', text: s }
    return <Tag color={i.color} style={{ fontSize: 10 }}>{i.text}</Tag>
  }
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 8, padding: '4px 12px 8px', borderLeft: '2px solid #FF2A2A' }}>
      {advices.map((a: any) => (
        <div key={a.id} style={{ border: '1px solid #2A2A2A', padding: 10, background: '#0c0c0c' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
            <span style={{ color: '#aaa', fontWeight: 600, fontSize: 11 }}>{a.audienceRole === 'DISPATCHER' ? '调度员建议' : '运维建议'}</span>
            {statusTag(a.status)}
          </div>
          {a.analysis && <Typography.Paragraph style={{ color: '#ccc', fontSize: 11, marginBottom: 4 }}>{a.analysis}</Typography.Paragraph>}
        </div>
      ))}
    </div>
  )
}

/* ════════════════════ 运维视图 — 工单列表 ════════════════════ */
const OperatorTickets = () => {
  const user = useAuthStore((s) => s.user)
  const [tickets, setTickets] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [detailOpen, setDetailOpen] = useState<number | null>(null)
  const [detailTicket, setDetailTicket] = useState<any>(null)
  const [actions, setActions] = useState<any[]>([])
  const [resolveOpen, setResolveOpen] = useState(false)
  const [resolution, setResolution] = useState('')

  const fetch = useCallback(async () => {
    setLoading(true)
    try {
      const resp: any = await api.get('/tickets', { params: { page, size: 20, status: statusFilter || undefined, assigneeUserId: user?.id } })
      setTickets(resp.records || [])
      setTotal(resp.total || 0)
    } catch { message.error('工单加载失败') }
    finally { setLoading(false) }
  }, [page, statusFilter, user?.id])
  useEffect(() => { fetch() }, [fetch])


  const doAction = async (path: string, body?: any) => {
    try { await api.put(path, body || {}); message.success('操作成功'); setDetailOpen(null); fetch() }
    catch (e: any) { message.error(e?.response?.data?.message || e.message) }
  }

  const openDetail = async (ticket: any) => {
    setDetailTicket(ticket)
    try { setActions(await api.get(`/tickets/${ticket.id}/actions`)) }
    catch { setActions([]) }
    setDetailOpen(ticket.id)
  }

  const columns: ColumnsType<any> = [
    { title: '编号', dataIndex: 'ticketNo', width: 140 },
    { title: '优先级', dataIndex: 'priority', width: 70, render: (v: string) => <Tag color={PRIORITY[v]?.color}>{PRIORITY[v]?.label}</Tag> },
    { title: '状态', dataIndex: 'status', width: 80, render: (v: string) => <Tag color={STATUS[v]?.color}>{STATUS[v]?.label}</Tag> },
    { title: '概要', dataIndex: 'summary', ellipsis: true },
    { title: '创建人', dataIndex: 'createdByName', width: 80 },
    { title: '时间', dataIndex: 'createdAt', width: 120, render: (v: string) => v ? dayjs(v).format('MM-DD HH:mm') : '-' },
    {
      title: '操作', key: 'acts', width: 180, render: (_: any, r: any) => (
        <Space size="small">
          <Button size="small" type="primary" onClick={() => openDetail(r)}>处理</Button>
          {r.status === 'PENDING' && (
            <Button size="small" icon={<CheckOutlined />} onClick={() => doAction(`/tickets/${r.id}/claim`)}>认领</Button>
          )}
          {r.status === 'IN_PROGRESS' && r.assigneeUserId === user?.id && (
            <Button size="small" icon={<CheckOutlined />} onClick={() => { setDetailTicket(r); setResolution(''); setResolveOpen(true) }}>解决</Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Segmented value={statusFilter} onChange={(v) => { setStatusFilter(v as string); setPage(1) }}
          options={[
            { label: '全部', value: '' },
            { label: '待处理', value: 'PENDING' },
            { label: '处理中', value: 'IN_PROGRESS' },
            { label: '已解决', value: 'RESOLVED' },
          ]} />
        <Button size="small" onClick={fetch}>刷新</Button>
      </Space>
      <Table rowKey="id" dataSource={tickets} columns={columns} loading={loading} size="small"
        pagination={{ current: page, pageSize: 20, total, showSizeChanger: false, onChange: (p) => { setPage(p) } }}
      />

      {/* 工单详情抽屉 */}
      <Modal title={detailTicket ? `工单 ${detailTicket.ticketNo}` : ''} open={detailOpen !== null} onCancel={() => setDetailOpen(null)} footer={null} width={560}>
        {detailTicket && (
          <div>
            <Descriptions size="small" bordered column={1}
              items={[
                { label: '状态', children: <Tag color={STATUS[detailTicket.status]?.color}>{STATUS[detailTicket.status]?.label}</Tag> },
                { label: '优先级', children: <Tag color={PRIORITY[detailTicket.priority]?.color}>{PRIORITY[detailTicket.priority]?.label}</Tag> },
                { label: '概要', children: detailTicket.summary },
                { label: '创建人', children: detailTicket.createdByName },
                { label: '处理人', children: detailTicket.assigneeName || '未分配' },
                { label: '创建时间', children: detailTicket.createdAt ? dayjs(detailTicket.createdAt).format('MM-DD HH:mm') : '-' },
                { label: '处理结果', children: detailTicket.resolution || '暂无' },
              ]}
              labelStyle={{ color: '#888', background: '#0c0c0c', width: 80 }}
              contentStyle={{ color: '#ccc', background: '#0e0e0e' }}
            />
            <div style={{ margin: '12px 0' }}>
              <Space>
                {detailTicket.status === 'PENDING' && <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => doAction(`/tickets/${detailTicket.id}/claim`)}>认领并处理</Button>}
                {detailTicket.status === 'IN_PROGRESS' && detailTicket.assigneeUserId === user?.id && (
                  <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => { setResolveOpen(true) }}>标记解决</Button>
                )}
              </Space>
            </div>
            <h4 style={{ color: '#aaa', fontSize: 12 }}>处置时间线</h4>
            {actions.length === 0 ? <span style={{ color: '#666' }}>暂无记录</span> : (
              <Timeline items={actions.map((a: any) => ({
                color: a.action === 'RESOLVE' ? 'green' : 'blue',
                children: <div style={{ fontSize: 11 }}><div style={{ color: '#aaa' }}><strong>{a.operatorName}</strong> ({a.action})</div>{a.note && <div style={{ color: '#888' }}>{a.note}</div>}<div style={{ color: '#555', fontSize: 10 }}>{a.createdAt ? dayjs(a.createdAt).format('MM-DD HH:mm:ss') : ''}</div></div>,
              }))} />
            )}
          </div>
        )}
      </Modal>

      <Modal title="填写处理结果" open={resolveOpen} onCancel={() => setResolveOpen(false)}
        onOk={() => { doAction(`/tickets/${detailTicket.id}/resolve`, { resolution }); setResolveOpen(false) }} okText="提交">
        <Input.TextArea rows={4} value={resolution} onChange={(e) => setResolution(e.target.value)} placeholder="描述处理过程和结果（必填）" />
      </Modal>
    </div>
  )
}

/* ════════════════════ 主入口 ════════════════════ */
const AlertCenter = () => {
  const role = useAuthStore((s) => s.user?.role)

  if (role === 'OPERATOR') return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
        <h1 style={{ margin: 0, fontSize: 16, fontWeight: 900, color: '#EAEAEA' }}><FileTextOutlined style={{ color: '#FF2A2A', marginRight: 8 }} />工单处置</h1>
      </div>
      <hr className="brutalist" />
      <OperatorTickets />
    </div>
  )

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
        <h1 style={{ margin: 0, fontSize: 16, fontWeight: 900, color: '#EAEAEA' }}><BellOutlined style={{ color: '#FF2A2A', marginRight: 8 }} />告警中心</h1>
      </div>
      <hr className="brutalist" />
      <DispatcherAlerts />
    </div>
  )
}

export default AlertCenter
