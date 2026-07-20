/**
 * 运维人员工单处理工作台
 * 桌面：左侧工单表格 + 右侧详情 Drawer
 * 移动端：工单列表 + 全屏详情 Drawer
 */
import { useState, useEffect, useCallback } from 'react'
import { Table, Tag, Button, Segmented, Space, message, Modal, Input, Empty, Skeleton } from 'antd'
import { FileTextOutlined, CheckOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import * as ticketApi from '../../services/ticketApi'
import type { Ticket } from '../../services/ticketApi'
import useAuthStore from '../../stores/useAuthStore'
import AlertTicketDetail from './shared/AlertTicketDetail'
import api from '../../services/api'

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

const OperatorTicketWorkspace = () => {
  const user = useAuthStore((s) => s.user)
  const userId = user?.id
  const [tickets, setTickets] = useState<Ticket[]>([])
  const [loading, setLoading] = useState(true)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [selectedTicket, setSelectedTicket] = useState<Ticket | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [alertInfo, setAlertInfo] = useState<any>(null)
  const [resolveOpen, setResolveOpen] = useState(false)
  const [resolution, setResolution] = useState('')
  const [isMobile, setIsMobile] = useState(window.innerWidth < 960)

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 960)
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  const fetch = useCallback(async () => {
    setLoading(true)
    try {
      const resp = await ticketApi.fetchTickets({
        page, size: 20,
        status: statusFilter || undefined,
        assigneeUserId: userId,
      })
      setTickets(resp.records || [])
      setTotal(resp.total || 0)
    } catch { message.error('工单加载失败') }
    finally { setLoading(false) }
  }, [page, statusFilter, userId])
  useEffect(() => { fetch() }, [fetch])

  // 监听 WebSocket 工单更新
  useEffect(() => {
    const handler = () => {
      fetch()
    }
    window.addEventListener('ws:ticket-update', handler as EventListener)
    return () => window.removeEventListener('ws:ticket-update', handler as EventListener)
  }, [fetch])

  const openDetail = async (ticket: Ticket) => {
    setSelectedTicket(ticket)
    setDetailOpen(true)
    if (ticket.sourceType === 'PREWARNING') {
      setAlertInfo({
        id: null,
        level: ticket.riskLevel || 'YELLOW',
        currentValue: ticket.expectedLoad || 0,
        thresholdValue: ticket.riskLevel === 'RED' ? 1210 : ticket.riskLevel === 'ORANGE' ? 1100 : 990,
        triggerTime: ticket.forecastTime || ticket.createdAt,
        aiAnalysis: '该工单基于预测风险提前创建，并非已触发告警。',
        suggestion: '请核对预测峰值、实时爬升趋势和阈值配置后再处理。',
      })
      return
    }
    // 加载关联告警信息
    try {
      await api.get(`/alert/events?page=1&size=1`) as any
    } catch {}
    // 构建告警信息
    try {
      const alertsResp = await api.get('/alert/events', { params: { page: 1, size: 50 } }) as any
      const alert = (alertsResp.records || []).find((a: any) => a.id === ticket.alertId)
      setAlertInfo(alert || {
        id: ticket.alertId,
        level: 'YELLOW',
        currentValue: 0,
        thresholdValue: 0,
        triggerTime: ticket.createdAt,
      })
    } catch {
      setAlertInfo({ id: ticket.alertId, level: 'YELLOW', currentValue: 0, thresholdValue: 0, triggerTime: ticket.createdAt })
    }
  }

  const handleTicketUpdate = (t: Ticket) => {
    setSelectedTicket(t)
    setTickets((prev) => prev.map((item) => item.id === t.id ? t : item))
  }

  const doAction = async (fn: () => Promise<Ticket>) => {
    try {
      const updated = await fn()
      handleTicketUpdate(updated)
      message.success('操作成功')
    } catch (e: any) {
      if (e?.response?.status === 409) {
        message.error('工单已被其他用户更新，请刷新')
        fetch()
      } else {
        message.error(e?.response?.data?.message || e.message)
      }
    }
  }

  const columns = [
    { title: '编号', dataIndex: 'ticketNo', width: 130 },
    { title: '来源', dataIndex: 'sourceType', width: 76, render: (v: string) => v === 'PREWARNING' ? <Tag color="gold">预警</Tag> : <Tag color="red">告警</Tag> },
    { title: '优先级', dataIndex: 'priority', width: 60, render: (v: string) => <Tag color={PRIORITY[v]?.color}>{PRIORITY[v]?.label}</Tag> },
    { title: '状态', dataIndex: 'status', width: 70, render: (v: string) => <Tag color={STATUS[v]?.color}>{STATUS[v]?.label}</Tag> },
    { title: '概要', dataIndex: 'summary', ellipsis: true },
    { title: '创建人', dataIndex: 'createdByName', width: 70 },
    { title: '处理人', dataIndex: 'assigneeName', width: 80, render: (v: string | null) => v || '--' },
    { title: 'SLA', dataIndex: 'slaStatus', width: 100, render: (v: string) => v === 'OVERDUE_RESPONSE' ? <Tag color="red">响应超时</Tag> : v === 'OVERDUE_PROCESSING' ? <Tag color="red">处理超时</Tag> : v === 'COMPLETED' ? <Tag color="default">已完成</Tag> : <Tag color="green">正常</Tag> },
    { title: '时间', dataIndex: 'createdAt', width: 110, render: (v: string) => v ? dayjs(v).format('MM-DD HH:mm') : '-' },
    {
      title: '', key: 'acts', width: 160,
      render: (_: any, r: Ticket) => (
        <Space size="small">
          <Button size="small" onClick={() => openDetail(r)}>详情</Button>
          {r.status === 'PENDING' && (
            <Button size="small" icon={<CheckOutlined />} onClick={(e) => { e.stopPropagation(); doAction(() => ticketApi.claimTicket(r.id)) }}>
              认领
            </Button>
          )}
          {r.status === 'IN_PROGRESS' && r.assigneeUserId === userId && (
            <Button size="small" icon={<CheckOutlined />} onClick={(e) => { e.stopPropagation(); setSelectedTicket(r); setResolution(''); setResolveOpen(true) }}>
              解决
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
        <h1 style={{ margin: 0, fontSize: 16, fontWeight: 900, color: '#EAEAEA' }}>
          <FileTextOutlined style={{ color: '#FF2A2A', marginRight: 8 }} />工单处置
        </h1>
      </div>
      <hr className="brutalist" />

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

      {loading && tickets.length === 0 ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : tickets.length === 0 ? (
        <Empty description="暂无工单" />
      ) : (
        <Table
          rowKey="id"
          dataSource={tickets}
          columns={columns}
          loading={loading}
          size="small"
          pagination={{ current: page, pageSize: 20, total, showSizeChanger: false, onChange: (p) => setPage(p) }}
        />
      )}

      {/* 详情 Drawer */}
      {selectedTicket && alertInfo && (
        <AlertTicketDetail
          open={detailOpen}
          onClose={() => setDetailOpen(false)}
          role="OPERATOR"
          userId={userId}
          alert={alertInfo}
          ticket={selectedTicket}
          onTicketUpdated={handleTicketUpdate}
          onAssign={() => {}}
          onResolve={() => { setResolveOpen(true) }}
          fullscreen={isMobile}
        />
      )}

      {/* 解决 Modal */}
      <Modal
        title="填写处理结果"
        open={resolveOpen}
        onCancel={() => setResolveOpen(false)}
        onOk={async () => {
          if (!resolution.trim()) { message.warning('请填写处理结果'); return }
          if (!selectedTicket) return
          try {
            const updated = await ticketApi.resolveTicket(selectedTicket.id, resolution)
            handleTicketUpdate(updated)
            message.success('已标记解决')
            setResolveOpen(false)
          } catch (e: any) { message.error(e?.response?.data?.message || e.message) }
        }}
        okText="提交"
      >
        <Input.TextArea rows={4} value={resolution} onChange={(e) => setResolution(e.target.value)}
          placeholder="描述处理过程和结果（必填）" />
      </Modal>
    </div>
  )
}

export default OperatorTicketWorkspace
