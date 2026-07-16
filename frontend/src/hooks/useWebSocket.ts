/**
 * WebSocket Hook — /topic/load /topic/alerts /topic/predictions
 *
 * 连接 → 订阅 → 拉取 REST 快照 → 合并快照 + WS 推送 → 实时曲线累积
 * 重连后重复快照合并，保证不丢点、不重复、不倒序。
 */
import { useEffect, useRef, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import useDashboardStore from '../stores/useDashboardStore'
import { fetchRealtimeRecent } from '../services/dataApi'
import type { WsLoadPayload, WsAlertPayload, WsPredictionPayload } from '../types/alert'

const WS_URL = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/dashboard`

export function useWebSocket() {
  const clientRef = useRef<Client | null>(null)
  const pendingRef = useRef<WsLoadPayload[]>([])

  const appendRealtime = useDashboardStore((s) => s.appendRealtimeLoad)
  const appendAlert = useDashboardStore((s) => s.appendAlert)
  const mergeRealtime = useDashboardStore((s) => s.mergeRealtimeLoads)
  const setForecast = useDashboardStore((s) => s.setForecast)
  const setWsConnected = useDashboardStore((s) => s.setWsConnected)

  /**
   * 拉取 REST 快照并合并 WebSocket 期间收到的消息
   */
  const fetchAndMerge = useCallback(async () => {
    try {
      const snapshots = await fetchRealtimeRecent(30)

      if (snapshots.length > 0) {
        mergeRealtime(snapshots)
      }
    } catch (err) {
      console.error('[WS] 快照拉取失败', err)
    }
  }, [mergeRealtime])

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
      setWsConnected(true)
      console.log('[WS] connected')

      // 1. 订阅 /topic/load — 累积实时点
      client.subscribe('/topic/load', (msg) => {
        try {
          const p: WsLoadPayload = JSON.parse(msg.body)
          if (p.type === 'load_update' && p.data) {
            appendRealtime({
              timestamp: p.data.timestamp,
              sequence: p.data.sequence,
              loadMw: p.data.loadMw,
              temperature: p.data.temperature ?? null,
              humidity: p.data.humidity ?? null,
              source: p.data.source,
            })
          }
        } catch (err) {
          console.error('[WS] /topic/load 解析失败', err, msg.body)
        }
      })

      // 2. 订阅 /topic/alerts
      client.subscribe('/topic/alerts', (msg) => {
        try {
          const p: WsAlertPayload = JSON.parse(msg.body)
          if (p.type === 'alert') {
            appendAlert({
              id: p.data.id,
              type: 'THRESHOLD',
              triggerTime: p.data.triggerTime,
              level: p.data.level,
              currentValue: p.data.currentValue,
              thresholdValue: p.data.thresholdValue,
              ruleId: 0,
              resolvedAt: null,
              aiAnalysis: p.data.aiAnalysis,
              suggestion: p.data.suggestion,
              isRead: 0,
              createdAt: new Date().toISOString(),
            })
          }
        } catch (err) {
          console.error('[WS] /topic/alerts 解析失败', err, msg.body)
        }
      })

      // 3. 订阅 /topic/predictions
      client.subscribe('/topic/predictions', (msg) => {
        try {
          const p: WsPredictionPayload = JSON.parse(msg.body)
          if (p.type === 'prediction_update') {
            setForecast({
              predictions: p.data.predictions,
              model: p.data.model,
              forecastStartTime: p.data.forecastStartTime ?? null,
            })
          }
        } catch (err) {
          console.error('[WS] /topic/predictions 解析失败', err, msg.body)
        }
      })

      // 4. 连接后拉取快照补偿缺口
      fetchAndMerge()
    }

    client.onDisconnect = () => {
      setWsConnected(false)
      console.log('[WS] disconnected')
    }

    client.onStompError = (f) => {
      console.error('[WS] STOMP error', f)
    }

    client.activate()
    clientRef.current = client
  }, [appendRealtime, appendAlert, setForecast, fetchAndMerge, setWsConnected])

  const disconnect = useCallback(() => {
    clientRef.current?.deactivate()
    clientRef.current = null
    setWsConnected(false)
  }, [setWsConnected])

  useEffect(() => {
    connect()
    return () => disconnect()
  }, [connect, disconnect])

  // 清理 pending buffer 引用
  useEffect(() => {
    return () => { pendingRef.current = [] }
  }, [])

  return { reconnect: connect, disconnect }
}
