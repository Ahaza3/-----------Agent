/**
 * WebSocket Hook — /topic/load(alerts/predictions
 * 负荷推送 → setLiveLoad（独立实时读数）
 * 告警推送 → appendAlert
 * 预测推送 → setForecast
 */
import { useEffect, useRef, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import useDashboardStore from '../stores/useDashboardStore'
import type { LoadData } from '../types/load'
import type { WsLoadPayload, WsAlertPayload, WsPredictionPayload } from '../types/alert'

const WS_URL = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/dashboard`

export function useWebSocket() {
  const clientRef = useRef<Client | null>(null)
  const setLiveLoad = useDashboardStore((s) => s.setLiveLoad)
  const appendAlert = useDashboardStore((s) => s.appendAlert)

  const connect = useCallback(() => {
    if (clientRef.current?.active) return

    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: {},
      debug: () => {},
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    })

    client.onConnect = () => {
      client.subscribe('/topic/load', (msg) => {
        try {
          const p: WsLoadPayload = JSON.parse(msg.body)
          if (p.type === 'load_update') {
            setLiveLoad({
              id: 0, isHoliday: 0,
              time: p.data.time,
              loadMw: p.data.loadMw,
              temperature: p.data.temperature,
              humidity: p.data.humidity,
              hour: new Date(p.data.time).getHours(),
              dayOfWeek: new Date(p.data.time).getDay(),
              month: new Date(p.data.time).getMonth() + 1,
              createdAt: new Date().toISOString(),
            })
          }
        } catch { /* ignore */ }
      })

      client.subscribe('/topic/alerts', (msg) => {
        try {
          const p: WsAlertPayload = JSON.parse(msg.body)
          if (p.type === 'alert') {
            appendAlert({
              id: p.data.id, type: 'THRESHOLD',
              triggerTime: p.data.triggerTime,
              level: p.data.level,
              currentValue: p.data.currentValue,
              thresholdValue: p.data.thresholdValue,
              ruleId: 0, resolvedAt: null,
              aiAnalysis: p.data.aiAnalysis,
              suggestion: p.data.suggestion,
              isRead: 0, createdAt: new Date().toISOString(),
            })
          }
        } catch { /* ignore */ }
      })

      client.subscribe('/topic/predictions', (msg) => {
        try {
          const p: WsPredictionPayload = JSON.parse(msg.body)
          if (p.type === 'prediction_update') {
            useDashboardStore.getState().setForecast({
              predictions: p.data.predictions,
              model: p.data.model,
            })
          }
        } catch { /* ignore */ }
      })

      console.log('[WS] connected')
    }

    client.onDisconnect = () => console.log('[WS] disconnected')
    client.onStompError = (f) => console.error('[WS] error', f)
    client.activate()
    clientRef.current = client
  }, [setLiveLoad, appendAlert])

  const disconnect = useCallback(() => {
    clientRef.current?.deactivate()
    clientRef.current = null
  }, [])

  useEffect(() => {
    connect()
    return () => disconnect()
  }, [connect, disconnect])

  return { reconnect: connect, disconnect }
}
