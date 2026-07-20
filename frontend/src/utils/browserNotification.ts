const notifiedAlertIds = new Set<number>()

export type BrowserNotificationState = 'unsupported' | 'default' | 'granted' | 'denied'

export function getBrowserNotificationState(): BrowserNotificationState {
  if (typeof window === 'undefined' || !('Notification' in window)) return 'unsupported'
  return Notification.permission
}

export async function enableBrowserNotifications(): Promise<BrowserNotificationState> {
  if (typeof window === 'undefined' || !('Notification' in window)) return 'unsupported'
  return Notification.requestPermission()
}

export function showAlertNotification(alert: {
  id?: number
  level?: string
  currentValue?: number | null
  thresholdValue?: number | null
}) {
  if (getBrowserNotificationState() !== 'granted') return
  if (alert.id != null && notifiedAlertIds.has(alert.id)) return
  if (alert.id != null) notifiedAlertIds.add(alert.id)

  const current = alert.currentValue != null ? `${alert.currentValue.toFixed(1)} MW` : '负荷值未知'
  const threshold = alert.thresholdValue != null ? `，阈值 ${alert.thresholdValue.toFixed(1)} MW` : ''
  try {
    new Notification(`电力${alert.level || 'UNKNOWN'}告警`, {
      body: `当前负荷 ${current}${threshold}，请及时查看告警中心。`,
      tag: alert.id != null ? `power-alert-${alert.id}` : 'power-alert',
    })
  } catch {
    // Permission can change between the state check and Notification construction.
  }
}
