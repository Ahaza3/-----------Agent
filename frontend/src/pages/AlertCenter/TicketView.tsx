import { useState, useEffect, useCallback } from 'react'
import { Tag, Button, Space, Drawer, message, Input, Modal, Select, Timeline, Descriptions, Spin, Empty } from 'antd'
import { PlusOutlined, UserSwitchOutlined, CheckOutlined, CloseOutlined, FileTextOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import api from '../../services/api'
import useAuthStore from '../../stores/useAuthStore'

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

const TicketView = ({ alertId, onCreated }: { alertId: number; onCreated?: () => void }) => {
  const user = useAuthStore((s) => s.user)
  const role = user?.role
  const [ticket, setTicket] = useState<any>(null)
  const [actions, setActions] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [summary, setSummary] = useState('')
  const [resolveOpen, setResolveOpen] = useState(false)
  const [resolution, setResolution] = useState('')
  const [assignOpen, setAssignOpen] = useState(false)
  const [assignUserId, setAssignUserId] = useState<number>()

  const refresh = useCallback(async () => {
    setLoading(true)
    try { setTicket(await api.get(`/alerts/${alertId}/ticket` as any)) }
    catch { setTicket(null) }
    finally { setLoading(false) }
  }, [alertId])

  const loadActions = useCallback(async (tid: number) => {
    try { setActions(await api.get(`/tickets/${tid}/actions`)) }
    catch { setActions([]) }
  }, [])

  useEffect(() => { refresh() }, [refresh])

  const doCreate = async () => {
    try {
      const t = await api.post(`/alerts/${alertId}/ticket`, { summary })
      setTicket(t); setCreateOpen(false); onCreated?.()
      message.success('工单已创建')
    } catch (e: any) { message.error(e?.response?.data?.message || e.message) }
  }

  const doAction = async (path: string, body?: any) => {
    try {
      const t = await api.put(path, body || {})
      setTicket(t); message.success('操作成功'); setDrawerOpen(false); setResolveOpen(false); setAssignOpen(false)
    } catch (e: any) { message.error(e?.response?.data?.message || e.message) }
  }

  if (loading) return <Spin size="small" />;
  if (!ticket) {
    // No ticket — show create button for DISPATCHER
    const canCreate = role === 'DISPATCHER' || role === 'SYSTEM_ADMIN'
    if (!canCreate) return <span style={{ color: '#666', fontSize: 12 }}>暂无工单</span>
    return (
      <div>
        <Button size="small" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>发起处置</Button>
        <Modal title="发起处置工单" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={doCreate} okText="创建">
          <Input.TextArea rows={3} value={summary} onChange={(e) => setSummary(e.target.value)}
            placeholder="工单概要（自动关联当前告警）" />
        </Modal>
      </div>
    )
  }

  const st = STATUS[ticket.status] || { label: ticket.status, color: 'default' }
  const pr = PRIORITY[ticket.priority] || { label: ticket.priority, color: 'default' }
  const isAssignee = user?.id === ticket.assigneeUserId
  const canAssign = (role === 'DISPATCHER' || role === 'SYSTEM_ADMIN') && (ticket.status === 'PENDING' || ticket.status === 'ASSIGNED')
  const canClaim = (role === 'OPERATOR' || role === 'SYSTEM_ADMIN') && (ticket.status === 'PENDING' || (ticket.status === 'ASSIGNED' && isAssignee))
  const canResolve = (role === 'OPERATOR' || role === 'SYSTEM_ADMIN') && ticket.status === 'IN_PROGRESS' && isAssignee
  const canClose = (role === 'DISPATCHER' || role === 'SYSTEM_ADMIN') && ticket.status === 'RESOLVED'
  const canCancel = (role === 'DISPATCHER' || role === 'SYSTEM_ADMIN') && ticket.status !== 'CLOSED' && ticket.status !== 'CANCELLED'

  return (
    <div>
      <Space size="small">
        <Tag color={pr.color}>{pr.label}</Tag>
        <Tag color={st.color}>{st.label}</Tag>
        <span style={{ color: '#aaa', fontSize: 11 }}>{ticket.ticketNo}</span>
        {ticket.assigneeName && <span style={{ color: '#888', fontSize: 11 }}>处理人: {ticket.assigneeName}</span>}
        <Button size="small" icon={<FileTextOutlined />} onClick={() => { loadActions(ticket.id); setDrawerOpen(true) }}>详情</Button>
      </Space>

      <Drawer title="工单详情" placement="right" width={480} open={drawerOpen} onClose={() => setDrawerOpen(false)}>
        <Descriptions column={1} size="small" bordered
          items={[
            { label: '编号', children: ticket.ticketNo },
            { label: '状态', children: <Tag color={st.color}>{st.label}</Tag> },
            { label: '优先级', children: <Tag color={pr.color}>{pr.label}</Tag> },
            { label: '概要', children: ticket.summary },
            { label: '创建人', children: ticket.createdByName },
            { label: '处理人', children: ticket.assigneeName || '未分配' },
            { label: '创建时间', children: ticket.createdAt ? dayjs(ticket.createdAt).format('MM-DD HH:mm') : '-' },
            { label: '处理结果', children: ticket.resolution || '暂无' },
          ]}
          labelStyle={{ color: '#888', background: '#0c0c0c', width: 80 }}
          contentStyle={{ color: '#ccc', background: '#0e0e0e' }}
        />
        <div style={{ margin: '12px 0' }}>
          <Space>
            {canAssign && <Button size="small" icon={<UserSwitchOutlined />} onClick={() => setAssignOpen(true)}>指派</Button>}
            {canClaim && <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => doAction(`/tickets/${ticket.id}/claim`)}>认领</Button>}
            {canResolve && <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => setResolveOpen(true)}>标记解决</Button>}
            {canClose && <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => doAction(`/tickets/${ticket.id}/close`)}>确认关闭</Button>}
            {canCancel && <Button size="small" danger icon={<CloseOutlined />} onClick={() => doAction(`/tickets/${ticket.id}/cancel`, { reason: '调度员取消' })}>取消</Button>}
          </Space>
        </div>
        <h4 style={{ color: '#aaa', fontSize: 12, marginTop: 16 }}>处置时间线</h4>
        {actions.length === 0 ? <Empty description="暂无记录" image={Empty.PRESENTED_IMAGE_SIMPLE} /> : (
          <Timeline items={actions.map((a: any) => ({
            color: a.action === 'RESOLVE' ? 'green' : a.action === 'CANCEL' ? 'red' : 'blue',
            children: (
              <div style={{ fontSize: 11 }}>
                <div style={{ color: '#aaa' }}><strong>{a.operatorName}</strong> ({a.operatorRole}) — {a.action}</div>
                {a.note && <div style={{ color: '#888' }}>{a.note}</div>}
                <div style={{ color: '#555', fontSize: 10 }}>{a.createdAt ? dayjs(a.createdAt).format('MM-DD HH:mm:ss') : ''}</div>
              </div>
            ),
          }))}
          />
        )}
      </Drawer>

      <Modal title="指派处理人" open={assignOpen} onCancel={() => setAssignOpen(false)}
        onOk={() => doAction(`/tickets/${ticket.id}/assign`, { assigneeUserId: assignUserId })} okText="确认">
        <Select style={{ width: '100%' }} placeholder="选择运维人员" value={assignUserId} onChange={setAssignUserId}
          options={[{ value: 3, label: 'operator (运维李四)' }]} />
      </Modal>
      <Modal title="填写处理结果" open={resolveOpen} onCancel={() => setResolveOpen(false)}
        onOk={() => doAction(`/tickets/${ticket.id}/resolve`, { resolution })} okText="提交">
        <Input.TextArea rows={4} value={resolution} onChange={(e) => setResolution(e.target.value)}
          placeholder="描述处理过程和结果（必填）" />
      </Modal>
    </div>
  )
}

export default TicketView
