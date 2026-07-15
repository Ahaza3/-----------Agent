/**
 * WebSocket 自定义 Hook — 原生 WebSocket → STOMP 三通道订阅
 */
import { useEffect, useRef, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import useDashboardStore from '../stores/useDashboardStore'
import type { LoadData } from '../types/load'
import type { WsLoadPayload, WsAlertPayload, WsPredictionPayload } from '../types/alert'

const WS_URL = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/dashboard`

/** 订阅并自动重连 */
export function useWebSocket() {
  const clientRef = useRef<Client | null>(null)
  const appendLoadData = useDashboardStore((s) => s.appendLoadData)
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
      // 实时负荷
      client.subscribe('/topic/load', (msg) => {
        try {
          const payload: WsLoadPayload = JSON.parse(msg.body)
          if (payload.type === 'load_update') {
            const item: LoadData = {
              id: 0,
              time: payload.data.time,
              loadMw: payload.data.loadMw,
              temperature: payload.data.temperature,
              humidity: payload.data.humidity,
              isHoliday: 0,
              hour: new Date(payload.data.time).getHours(),
              dayOfWeek: new Date(payload.data.time).getDay(),
              month: new Date(payload.data.time).getMonth() + 1,
              createdAt: new Date().toISOString(),
            }
            appendLoadData(item)
          }
        } catch { /* ignore parse error */ }
      })

      // 告警推送
      client.subscribe('/topic/alerts', (msg) => {
        try {
          const payload: WsAlertPayload = JSON.parse(msg.body)
          if (payload.type === 'alert') {
            appendAlert({
              id: payload.data.id,
              triggerTime: payload.data.triggerTime,
              level: payload.data.level,
              type: 'THRESHOLD',
              currentValue: payload.data.currentValue,
              thresholdValue: payload.data.thresholdValue,
              ruleId: 0,
              aiAnalysis: payload.data.aiAnalysis,
              suggestion: payload.data.suggestion,
              isRead: 0,
              resolvedAt: null,
              createdAt: new Date().toISOString(),
            })
          }
        } catch { /* ignore */ }
      })

      // 预测推送
      client.subscribe('/topic/predictions', (msg) => {
        try {
          const payload: WsPredictionPayload = JSON.parse(msg.body)
          if (payload.type === 'prediction_update') {
            useDashboardStore.getState().setForecast({
              predictions: payload.data.predictions,
              model: payload.data.model,
            })
          }
        } catch { /* ignore */ }
      })

      console.log('[WS] connected')
    }

    client.onDisconnect = () => console.log('[WS] disconnected')
    client.onStompError = (f) => console.error('[WS] STOMP error', f)

    client.activate()
    clientRef.current = client
  }, [appendLoadData, appendAlert])

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
