/**
 * 告警中心 — 告警事件列表 + 筛选
 * P0 · Sprint 2 (Day 9)
 */
import { useState, useEffect, useCallback, useRef } from 'react'
import { Table, Tag, Button, Segmented, Space, message, Badge, Spin, Typography } from 'antd'
import {
  BellOutlined,
  CheckCircleOutlined,
  AlertOutlined,
  RobotOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import { fetchAlertEvents, markAlertRead } from '../../services/alertApi'
import api from '../../services/api'
import { ALERT_LEVEL_CONFIG } from '../../types/alert'
import type { AlertEvent, AlertLevel } from '../../types/alert'
import type { ColumnsType } from 'antd/es/table'
import useDashboardStore from '../../stores/useDashboardStore'

type LevelFilter = AlertLevel | 'ALL'

/* ─── AI 建议面板 ─── */
const AlertAdvicePanel = ({ alertId }: { alertId: number }) => {
  const [advice, setAdvice] = useState<any[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    api.get(`/alert/events/${alertId}/advice`)
      .then((d: any) => setAdvice(Array.isArray(d) ? d : []))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [alertId])

  if (loading) return <div style={{ padding: 16 }}><Spin size="small" /> 加载建议中…</div>
  if (!advice.length) return <div style={{ padding: 16, color: '#666' }}>暂无 AI 建议（异步生成中或 LLM 未配置）</div>

  const statusTag = (s: string) => {
    const map: Record<string, { color: string; text: string }> = {
      PENDING: { color: 'default', text: '生成中' },
      SUCCESS: { color: 'green', text: 'AI 已生成' },
      FAILED: { color: 'red', text: '生成失败' },
      FALLBACK: { color: 'gold', text: '降级建议' },
    }
    const m = map[s] || { color: 'default', text: s }
    return <Tag color={m.color} style={{ fontSize: 10 }}>{m.text}</Tag>
  }

  const roleLabel = (r: string) => r === 'DISPATCHER' ? '调度员' : '运维管理员'

  return (
    <div className="alert-detail">
      {advice.map((a: any) => (
        <div key={a.id} style={{ border: '1px solid #2A2A2A', padding: 12, background: '#0c0c0c' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
            <span style={{ color: '#aaa', fontWeight: 600 }}>
              <RobotOutlined style={{ marginRight: 4, color: '#FF2A2A' }} />
              {roleLabel(a.audienceRole)}建议
            </span>
            {statusTag(a.status)}
          </div>
          {a.analysis && <Typography.Paragraph style={{ color: '#ccc', fontSize: 12, marginBottom: 8 }}>{a.analysis}</Typography.Paragraph>}
          {a.actions && (
            <ul style={{ color: '#aaa', fontSize: 11, margin: 0, paddingLeft: 16 }}>
              {(() => { try { return JSON.parse(a.actions).map((act: string, i: number) => <li key={i}>{act}</li>) } catch { return <li>{a.actions}</li> } })()}
            </ul>
          )}
          {a.modelName && <div style={{ color: '#555', fontSize: 10, marginTop: 6 }}>模型: {a.modelName}</div>}
          {a.generatedAt && <div style={{ color: '#555', fontSize: 10 }}>{dayjs(a.generatedAt).format('MM-DD HH:mm:ss')}</div>}
        </div>
      ))}
    </div>
  )
}

const AlertCenter = () => {
  const [events, setEvents] = useState<AlertEvent[]>([])
  const [levelFilter, setLevelFilter] = useState<LevelFilter>('ALL')
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [loading, setLoading] = useState(false)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const wsAlerts = useDashboardStore((s) => s.alerts)
  const acknowledgeStoreAlert = useDashboardStore((s) => s.acknowledgeAlert)
  const seenIds = useRef(new Set<number>())

  // 实时注入 WebSocket 新告警
  useEffect(() => {
    if (wsAlerts.length === 0) return
    const fresh = wsAlerts.filter((a) => !seenIds.current.has(a.id))
    if (fresh.length === 0) return
    fresh.forEach((a) => seenIds.current.add(a.id))
    setEvents((prev) => {
      const existing = new Set(prev.map((e) => e.id))
      const toAdd = fresh.filter((a) => !existing.has(a.id))
      return [...toAdd, ...prev]
    })
    setTotal((t) => t + fresh.length)
  }, [wsAlerts])

  const fetch = useCallback(async (p: number) => {
    setLoading(true)
    try {
      const result = await fetchAlertEvents({
        page: p,
        size: 20,
        level: levelFilter === 'ALL' ? undefined : levelFilter,
        unreadOnly,
      })
      setEvents(result.records)
      setTotal(result.total)
    } catch {
      message.error('告警数据加载失败')
    } finally {
      setLoading(false)
    }
  }, [levelFilter, unreadOnly])

  useEffect(() => {
    setPage(1)
    fetch(1)
  }, [fetch])

  const handleMarkRead = async (id: number) => {
    try {
      await markAlertRead(id)
      acknowledgeStoreAlert(id)
      setEvents((prev) =>
        prev.map((e) => (e.id === id ? { ...e, isRead: 1 } : e)),
      )
    } catch {
      message.error('标记失败')
    }
  }

  const columns: ColumnsType<AlertEvent> = [
    {
      title: '',
      dataIndex: 'isRead',
      width: 40,
      render: (v: number) =>
        v === 0 ? <Badge status="error" /> : <Badge status="default" />,
    },
    {
      title: '级别',
      dataIndex: 'level',
      width: 80,
      render: (v: AlertLevel) => {
        const cfg = ALERT_LEVEL_CONFIG[v]
        return <Tag color={cfg?.color}>{cfg?.label ?? v}</Tag>
      },
    },
    {
      title: '触发时间',
      dataIndex: 'triggerTime',
      width: 180,
      render: (v: string) => dayjs(v).format('MM-DD HH:mm:ss'),
      sorter: (a, b) => dayjs(a.triggerTime).unix() - dayjs(b.triggerTime).unix(),
    },
    {
      title: '当前值',
      dataIndex: 'currentValue',
      width: 100,
      render: (v: number) => `${v?.toFixed(1)} MW`,
    },
    {
      title: '阈值',
      dataIndex: 'thresholdValue',
      width: 100,
      render: (v: number) => `${v?.toFixed(0)} MW`,
    },
    {
      title: '分析',
      dataIndex: 'aiAnalysis',
      ellipsis: true,
      render: (v: string) => (
        <span style={{ color: '#8892a4', fontSize: 13 }}>{v}</span>
      ),
    },
    {
      title: '',
      key: 'action',
      width: 80,
      render: (_, record) =>
        record.isRead === 0 ? (
          <Button
            type="link"
            size="small"
            icon={<CheckCircleOutlined />}
            onClick={() => handleMarkRead(record.id)}
            style={{ color: '#4AF626' }}
          >
            已读
          </Button>
        ) : (
          <span style={{ color: '#555', fontSize: 12 }}>已确认</span>
        ),
    },
  ]

  return (
    <div style={{ padding: '0 0 24px' }}>
      {/* ====== 标题栏 ====== */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 20,
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <h1 style={{ margin: 0, fontSize: 16, fontWeight: 900, color: '#EAEAEA', letterSpacing: '-0.02em' }}>
          <AlertOutlined style={{ color: '#FF2A2A', marginRight: 8 }} />
          告警中心
          <span style={{ color: '#666', margin: '0 6px', fontSize: 12 }}>//</span>
          <span style={{ color: '#888', fontSize: 11, letterSpacing: '0.04em' }}>
            {total} 条记录
          </span>
        </h1>

        <Space>
          <Segmented
            value={levelFilter}
            onChange={(v) => setLevelFilter(v as LevelFilter)}
            options={[
              { label: '全部', value: 'ALL' },
              { label: '紧急', value: 'RED' },
              { label: '重要', value: 'ORANGE' },
              { label: '提示', value: 'YELLOW' },
            ]}
          />
          <Button
            type={unreadOnly ? 'primary' : 'default'}
            danger={unreadOnly}
            onClick={() => setUnreadOnly(!unreadOnly)}
            icon={<BellOutlined />}
          >
            仅未读
          </Button>
        </Space>
      </div>

      {/* ====== 表格 ====== */}
      <Table
        columns={columns}
        dataSource={events}
        rowKey="id"
        loading={loading}
        size="middle"
        pagination={{
          current: page,
          total,
          pageSize: 20,
          showSizeChanger: false,
          showTotal: (t) => `${t} 条告警`,
          onChange: (p) => {
            setPage(p)
            fetch(p)
          },
        }}
        expandable={{
          expandedRowRender: (record) => <AlertAdvicePanel alertId={record.id} />,
        }}
        locale={{ emptyText: '暂无告警，系统运行正常' }}
        style={{ background: 'transparent' }}
      />
      <style>{`
        .alert-detail {
          display: grid;
          grid-template-columns: repeat(2, minmax(0, 1fr));
          gap: 12px;
          padding: 8px 12px 12px;
          border-left: 2px solid #FF2A2A;
        }
        @media (max-width: 720px) {
          .alert-detail { grid-template-columns: 1fr; gap: 8px; }
        }
      `}</style>
    </div>
  )
}

export default AlertCenter
