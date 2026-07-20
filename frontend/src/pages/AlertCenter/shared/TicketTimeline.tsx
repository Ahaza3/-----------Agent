/**
 * 处置时间线组件 — 显示工单操作的时间线
 */
import { Timeline, Empty } from 'antd'
import dayjs from 'dayjs'

export interface TimelineEntry {
  id: number
  action: string
  fromStatus: string | null
  toStatus: string
  operatorName: string
  operatorRole: string
  note: string | null
  createdAt: string
}

const ACTION_LABELS: Record<string, string> = {
  CREATE: '创建工单',
  ASSIGN: '指派',
  CLAIM: '认领',
  START: '开始处理',
  RESOLVE: '标记解决',
  CLOSE: '关闭',
  CANCEL: '取消',
}

const TicketTimeline = ({ actions }: { actions: TimelineEntry[] }) => {
  if (!actions.length) {
    return <Empty description="暂无处置记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
  }

  return (
    <Timeline
      items={actions.map((a) => ({
        color: a.action === 'RESOLVE'
          ? 'green'
          : a.action === 'CANCEL'
            ? 'red'
            : a.action === 'CLOSE'
              ? 'green'
              : 'blue',
        children: (
          <div style={{ fontSize: 11 }}>
            <div style={{ color: '#aaa' }}>
              <strong>{a.operatorName}</strong>
              <span style={{ color: '#666' }}>（{a.operatorRole === 'DISPATCHER' ? '调度员' : a.operatorRole === 'OPERATOR' ? '运维' : a.operatorRole}）</span>
              {' — '}{ACTION_LABELS[a.action] || a.action}
            </div>
            {a.note && <div style={{ color: '#888' }}>{a.note}</div>}
            <div style={{ color: '#555', fontSize: 10 }}>
              {a.createdAt ? dayjs(a.createdAt).format('MM-DD HH:mm:ss') : ''}
            </div>
          </div>
        ),
      }))}
    />
  )
}

export default TicketTimeline
