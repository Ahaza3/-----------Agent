/**
 * 工单操作栏 — 根据角色和状态显示可用操作
 * 操作期间 loading，禁止重复点击，危险操作二次确认
 */
import { useState, useCallback } from 'react'
import { Button, Space, Popconfirm, Tooltip } from 'antd'
import {
  CheckOutlined,
  UserSwitchOutlined,
  CloseOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons'
import type { Role } from '../../../config/roles'
import { ROLE_CONFIG } from '../../../config/roles'

export interface TicketActionBarProps {
  role: Role | undefined
  userId: number | undefined
  ticket: {
    id: number
    status: string
    priority: string
    assigneeUserId: number | null
  }
  /** 点击指派人 */
  onAssign: () => void
  /** 认领 */
  onClaim: () => Promise<void>
  /** 开始处理 */
  onStart: () => Promise<void>
  /** 标记解决（会弹出填写结果的 modal） */
  onResolve: () => void
  /** 关闭 */
  onClose: () => Promise<void>
  /** 取消 */
  onCancel: () => Promise<void>
  disabled?: boolean
}

const TicketActionBar = ({
  role, userId, ticket, onAssign, onClaim, onStart, onResolve, onClose, onCancel, disabled,
}: TicketActionBarProps) => {
  const [claimLoading, setClaimLoading] = useState(false)
  const [startLoading, setStartLoading] = useState(false)
  const [closeLoading, setCloseLoading] = useState(false)
  const [cancelLoading, setCancelLoading] = useState(false)

  const handleClaim = useCallback(async () => {
    setClaimLoading(true)
    try { await onClaim() } finally { setClaimLoading(false) }
  }, [onClaim])

  const handleStart = useCallback(async () => {
    setStartLoading(true)
    try { await onStart() } finally { setStartLoading(false) }
  }, [onStart])

  const handleClose = useCallback(async () => {
    setCloseLoading(true)
    try { await onClose() } finally { setCloseLoading(false) }
  }, [onClose])

  const handleCancel = useCallback(async () => {
    setCancelLoading(true)
    try { await onCancel() } finally { setCancelLoading(false) }
  }, [onCancel])

  const actions = role ? ROLE_CONFIG[role]?.ticketActions : null
  if (!actions) return null

  const isAssignee = userId != null && userId === ticket.assigneeUserId
  const canAssign = actions.assign && (ticket.status === 'PENDING' || ticket.status === 'ASSIGNED')
  const canClaim = actions.claim && ticket.status === 'PENDING'
  const canStart = actions.start && ticket.status === 'ASSIGNED' && isAssignee
  const canResolve = actions.resolve && ticket.status === 'IN_PROGRESS' && isAssignee
  const canClose = actions.close && ticket.status === 'RESOLVED'
  const canCancel = actions.cancel && ticket.status !== 'CLOSED' && ticket.status !== 'CANCELLED'

  return (
    <Space size="small" wrap>
      {canAssign && (
        <Tooltip title="指派运维人员处理">
          <Button size="small" icon={<UserSwitchOutlined />} onClick={onAssign} disabled={disabled}>
            指派
          </Button>
        </Tooltip>
      )}
      {canClaim && (
        <Tooltip title="认领工单并开始处理">
          <Button size="small" type="primary" icon={<CheckOutlined />} loading={claimLoading} disabled={disabled} onClick={handleClaim}>
            认领
          </Button>
        </Tooltip>
      )}
      {canStart && (
        <Tooltip title="开始处理工单">
          <Button size="small" icon={<PlayCircleOutlined />} loading={startLoading} disabled={disabled} onClick={handleStart}>
            开始处理
          </Button>
        </Tooltip>
      )}
      {canResolve && (
        <Tooltip title="填写处理结果并标记为已解决">
          <Button size="small" type="primary" icon={<CheckOutlined />} disabled={disabled} onClick={onResolve}>
            标记解决
          </Button>
        </Tooltip>
      )}
      {canClose && (
        <Popconfirm
          title="确认关闭"
          description="确认处置完成并关闭该工单？"
          onConfirm={handleClose}
          okText="确认关闭"
          cancelText="取消"
        >
          <Button size="small" type="primary" icon={<CheckOutlined />} loading={closeLoading} disabled={disabled}>
            确认关闭
          </Button>
        </Popconfirm>
      )}
      {canCancel && (
        <Popconfirm
          title="确认取消"
          description="确定要取消该工单吗？"
          onConfirm={handleCancel}
          okText="确认取消"
          cancelText="返回"
          okButtonProps={{ danger: true }}
        >
          <Button size="small" danger icon={<CloseOutlined />} loading={cancelLoading} disabled={disabled}>
            取消工单
          </Button>
        </Popconfirm>
      )}
    </Space>
  )
}

export default TicketActionBar
