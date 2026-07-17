/**
 * 告警/工单中心入口 — 按角色路由到不同的工作台
 *   DISPATCHER → 告警处置
 *   OPERATOR   → 工单处置
 */
import useAuthStore from '../../stores/useAuthStore'
import type { Role } from '../../config/roles'
import DispatcherAlertWorkspace from './DispatcherAlertWorkspace'
import OperatorTicketWorkspace from './OperatorTicketWorkspace'

const AlertCenter = () => {
  const role = useAuthStore((s) => s.user?.role) as Role | undefined

  if (role === 'OPERATOR') return <OperatorTicketWorkspace />

  return <DispatcherAlertWorkspace />
}

export default AlertCenter
