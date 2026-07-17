/**
 * 调度员告警处置工作台
 * 桌面：左侧告警列表 + 右侧详情 Drawer
 * 移动端：告警列表 + 全屏详情 Drawer
 */
import { useState, useEffect, useCallback, useRef } from 'react'
import { Table, Tag, Button, Segmented, Space, message, Badge, Modal, Select, Input, Empty, Skeleton } from 'antd'
import { BellOutlined, FileTextOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { fetchAlertEvents, markAlertRead } from '../../services/alertApi'
import { fetchTicketByAlert, createTicket, assignTicket, fetchAssignees } from '../../services/ticketApi'
import type { AssigneeInfo, Ticket } from '../../services/ticketApi'
import { ALERT_LEVEL_CONFIG } from '../../types/alert'
import type { AlertEvent, AlertLevel } from '../../types/alert'
import type { ColumnsType } from 'antd/es/table'
import useDashboardStore from '../../stores/useDashboardStore'
import useAuthStore from '../../stores/useAuthStore'
import AlertTicketDetail from './shared/AlertTicketDetail'

type LevelFilter = AlertLevel | 'ALL'

const DispatcherAlertWorkspace = () => {
  const [events, setEvents] = useState<AlertEvent[]>([])
  const [levelFilter, setLevelFilter] = useState<LevelFilter>('ALL')
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [loading, setLoading] = useState(false)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [ticketMap, setTicketMap] = useState<Record<number, Ticket | null>>({})
  const [selectedAlert, setSelectedAlert] = useState<AlertEvent | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [creatingId, setCreatingId] = useState<number | null>(null)
  const [summary, setSummary] = useState('')
  const [assignOpen, setAssignOpen] = useState(false)
  const [assignees, setAssignees] = useState<AssigneeInfo[]>([])
  const [assignUserId, setAssignUserId] = useState<number | undefined>()
  const [isMobile, setIsMobile] = useState(window.innerWidth < 960)
  const user = useAuthStore((s) => s.user)
  const userId = user?.id

  const wsAlerts = useDashboardStore((s) => s.alerts)
  const acknowledgeStoreAlert = useDashboardStore((s) => s.acknowledgeAlert)
  const seenIds = useRef(new Set<number>())

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 960)
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  // WebSocket 告警去重
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

  // 批量加载工单
  useEffect(() => {
    if (events.length === 0) return
    const fetchTickets = async () => {
      const map: Record<number, Ticket | null> = {}
      await Promise.allSettled(events.map(async (e) => {
        try { map[e.id] = await fetchTicketByAlert(e.id) } catch { map[e.id] = null }
      }))
      setTicketMap(map)
    }
    fetchTickets()
  }, [events])

  // 监听 WebSocket 工单更新
  useEffect(() => {
    const handler = (e: CustomEvent) => {
      if (e.detail?.data?.id) {
        fetch(page) // 刷新列表
      }
    }
    window.addEventListener('ws:ticket-update', handler as EventListener)
    return () => window.removeEventListener('ws:ticket-update', handler as EventListener)
  }, [page, fetch])

  const handleMarkRead = async (id: number) => {
    try { await markAlertRead(id); setEvents((prev) => prev.map((e) => e.id === id ? { ...e, isRead: 1 } : e)) }
    catch { message.error('操作失败') }
  }

  const loadAssignees = async () => {
    try {
      const list = await fetchAssignees()
      setAssignees(list)
      if (list.length === 0) message.info('暂无可指派的运维人员')
    } catch { message.error('加载运维人员失败') }
  }

  const handleCreateTicket = async () => {
    if (!creatingId) return
    try {
      const t = await createTicket(creatingId, summary)
      if (assignUserId) await assignTicket(t.id, assignUserId)
      message.success('工单已创建' + (assignUserId ? '并指派' : ''))
      setTicketMap((prev) => ({ ...prev, [creatingId]: t }))
      setCreateOpen(false)
      setCreatingId(null)
      setSummary('')
      setAssignUserId(undefined)
    } catch (e: any) { message.error(e?.response?.data?.message || e.message) }
  }

  const handleTicketUpdate = (t: Ticket) => {
    setTicketMap((prev) => ({ ...prev, [t.alertId]: t }))
  }

  const openDetail = async (record: AlertEvent) => {
    setSelectedAlert(record)
    if (!ticketMap[record.id]) {
      try {
        const t = await fetchTicketByAlert(record.id)
        setTicketMap((prev) => ({ ...prev, [record.id]: t }))
      } catch { setTicketMap((prev) => ({ ...prev, [record.id]: null })) }
    }
    setDetailOpen(true)
  }

  const statusTag = (alertId: number, ticket: Ticket | null | undefined) => {
    if (!ticket) return <Button size="small" type="default" icon={<FileTextOutlined />} onClick={(e) => { e.stopPropagation(); setCreatingId(alertId); setSummary(''); loadAssignees(); setCreateOpen(true) }}>发起处置</Button>
    const st: Record<string, any> = { PENDING: { label: '待处理', color: 'default' }, ASSIGNED: { label: '已指派', color: 'blue' }, IN_PROGRESS: { label: '处理中', color: 'processing' }, RESOLVED: { label: '已解决', color: 'green' }, CLOSED: { label: '已关闭', color: 'default' }, CANCELLED: { label: '已取消', color: 'default' } }
    const s = st[ticket.status] || { label: ticket.status, color: 'default' }
    return <Tag color={s.color}>{s.label}</Tag>
  }

  const alertColumns: ColumnsType<AlertEvent> = [
    { title: '级别', dataIndex: 'level', width: 70, render: (v: AlertLevel) => <Tag color={ALERT_LEVEL_CONFIG[v]?.color}>{ALERT_LEVEL_CONFIG[v]?.label}</Tag> },
    { title: '当前负荷', dataIndex: 'currentValue', width: 90, render: (v: number) => `${v?.toFixed(1)} MW` },
    { title: '阈值', dataIndex: 'thresholdValue', width: 80, render: (v: number) => `${v?.toFixed(0)} MW` },
    { title: '时间', dataIndex: 'triggerTime', width: 130, render: (v: string) => v ? dayjs(v).format('MM-DD HH:mm:ss') : '-' },
    { title: '工单', dataIndex: 'id', width: 100, render: (id: number) => statusTag(id, ticketMap[id]) },
    { title: '已读', dataIndex: 'isRead', width: 50, render: (v: number) => v === 0 ? <Badge status="error" /> : <Badge status="default" /> },
    {
      title: '', key: 'actions', width: 80, render: (_: any, r: AlertEvent) => (
        <Space size="small">
          {r.isRead === 0 && <Button size="small" onClick={(e) => { e.stopPropagation(); handleMarkRead(r.id) }}>已读</Button>}
          <Button size="small" type="link" onClick={() => openDetail(r)}>详情</Button>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
        <h1 style={{ margin: 0, fontSize: 16, fontWeight: 900, color: '#EAEAEA' }}>
          <BellOutlined style={{ color: '#FF2A2A', marginRight: 8 }} />告警中心
        </h1>
      </div>
      <hr className="brutalist" />

      <Space style={{ marginBottom: 12 }}>
        <Segmented value={levelFilter} onChange={(v) => { setLevelFilter(v as LevelFilter); setPage(1) }}
          options={[
            { label: '全部', value: 'ALL' },
            { label: (<span style={{ color: '#FF2A2A' }}>紧急</span>), value: 'RED' },
            { label: (<span style={{ color: '#FA8C16' }}>重要</span>), value: 'ORANGE' },
            { label: (<span style={{ color: '#FADB14' }}>提示</span>), value: 'YELLOW' },
          ]} />
        <Button type={unreadOnly ? 'primary' : 'default'} size="small" onClick={() => setUnreadOnly(!unreadOnly)}>仅未读</Button>
        <Button size="small" onClick={() => fetch()}>刷新</Button>
      </Space>

      {loading && events.length === 0 ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : events.length === 0 ? (
        <Empty description="暂无告警事件" />
      ) : (
        <Table
          rowKey="id"
          dataSource={events}
          columns={alertColumns}
          loading={loading}
          size="small"
          onRow={(record) => ({ onClick: () => openDetail(record), style: { cursor: 'pointer' } })}
          pagination={{ current: page, pageSize: 20, total, showSizeChanger: false, onChange: (p) => { setPage(p); fetch(p) } }}
        />
      )}

      {/* 详情 Drawer */}
      {selectedAlert && (
        <AlertTicketDetail
          open={detailOpen}
          onClose={() => setDetailOpen(false)}
          role="DISPATCHER"
          userId={userId}
          alert={{
            id: selectedAlert.id,
            level: selectedAlert.level,
            currentValue: selectedAlert.currentValue,
            thresholdValue: selectedAlert.thresholdValue,
            triggerTime: selectedAlert.triggerTime,
            aiAnalysis: selectedAlert.aiAnalysis,
            suggestion: selectedAlert.suggestion,
          }}
          ticket={ticketMap[selectedAlert.id] || null}
          onTicketUpdated={handleTicketUpdate}
          onAssign={() => { loadAssignees(); setAssignOpen(true); setAssignUserId(undefined) }}
          onResolve={() => {}}
          fullscreen={isMobile}
        />
      )}

      {/* 创建工单 Modal */}
      <Modal title="发起处置工单" open={createOpen} onCancel={() => { setCreateOpen(false); setAssignUserId(undefined) }} onOk={handleCreateTicket} okText="创建">
        <Input.TextArea rows={3} value={summary} onChange={(e) => setSummary(e.target.value)} placeholder="工单概要" style={{ marginBottom: 12 }} />
        <Select
          style={{ width: '100%' }}
          placeholder="指派运维人员（可选）"
          value={assignUserId}
          onChange={setAssignUserId}
          allowClear
          showSearch
          optionFilterProp="label"
          options={assignees.map((a) => ({ value: a.id, label: `${a.displayName} (${a.username})` }))}
          notFoundContent={<Empty description="暂无可指派的运维人员" image={Empty.PRESENTED_IMAGE_SIMPLE} />}
        />
      </Modal>

      {/* 指派 Modal */}
      <Modal title="指派处理人" open={assignOpen} onCancel={() => setAssignOpen(false)}
        onOk={async () => {
          if (!assignUserId || !selectedAlert || !ticketMap[selectedAlert.id]) return
          try {
            const t = await assignTicket(ticketMap[selectedAlert.id]!.id, assignUserId)
            handleTicketUpdate(t)
            message.success('已指派')
            setAssignOpen(false)
          } catch (e: any) { message.error(e?.response?.data?.message || e.message) }
        }} okText="确认">
        <Select
          style={{ width: '100%' }}
          placeholder="选择运维人员"
          value={assignUserId}
          onChange={setAssignUserId}
          showSearch
          optionFilterProp="label"
          options={assignees.map((a) => ({ value: a.id, label: `${a.displayName} (${a.username})` }))}
          notFoundContent={<Empty description="暂无可指派的运维人员" image={Empty.PRESENTED_IMAGE_SIMPLE} />}
        />
      </Modal>
    </div>
  )
}

export default DispatcherAlertWorkspace
