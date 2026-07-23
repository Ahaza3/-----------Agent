/**
 * WebSocket Hook — /topic/load /topic/alerts /topic/predictions /topic/tickets /topic/alert-advice
 *
 * 连接 → 订阅 → 拉取 REST 快照 → 合并快照 + WS 推送
 * 重连后重复快照合并，保证不丢点、不重复、不倒序。
 * 认证 Token 通过 URL query param 传递（STOMP 握手阶段 JwtHandshakeInterceptor 校验）。
 */
import { useEffect, useRef, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import useDashboardStore from '../stores/useDashboardStore'
import useAuthStore from '../stores/useAuthStore'
import useSystemStatusStore from '../stores/useSystemStatusStore'
import { fetchRealtimeRecent } from '../services/dataApi'
import type { WsLoadPayload, WsAlertPayload, WsPredictionPayload } from '../types/alert'
import { showAlertNotification } from '../utils/browserNotification'

const WS_BASE = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/dashboard`

function buildWsUrl(): string {
  const token = useAuthStore.getState().accessToken
  if (!token) return WS_BASE
  return `${WS_BASE}?token=${encodeURIComponent(token)}`
}

export function useWebSocket() {
  const clientRef = useRef<Client | null>(null)
  const ticketCacheRef = useRef<Map<number, any>>(new Map())
  const adviceCacheRef = useRef<Map<number, any>>(new Map())

  const appendRealtime = useDashboardStore((s) => s.appendRealtimeLoad)
  const appendAlert = useDashboardStore((s) => s.appendAlert)
  const mergeRealtime = useDashboardStore((s) => s.mergeRealtimeLoads)
  const setForecast = useDashboardStore((s) => s.setForecast)
  const setWsConnected = useDashboardStore((s) => s.setWsConnected)
  const setWsState = useSystemStatusStore((s) => s.setWsState)
  const setLastRealtimeAt = useSystemStatusStore((s) => s.setLastRealtimeAt)
  const setDataSource = useSystemStatusStore((s) => s.setDataSource)

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

    const token = useAuthStore.getState().accessToken
    if (!token) return

    setWsState('CONNECTING')

    const client = new Client({
      brokerURL: buildWsUrl(),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      debug: () => {},
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    })

    client.onConnect = () => {
      setWsConnected(true)
      setWsState('CONNECTED')
      console.log('[WS] connected')

      // 1. /topic/load
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
              nodeId: p.data.nodeId,
              observedAt: p.data.observedAt ?? null,
              receivedAt: p.data.receivedAt ?? null,
              sourceInstanceId: p.data.sourceInstanceId ?? null,
              qualityCode: p.data.qualityCode,
              qualityReason: p.data.qualityReason ?? null,
              dataSource: p.data.dataSource ?? null,
              estimated: p.data.estimated ?? false,
              freshnessStatus: p.data.freshnessStatus,
              persistenceStatus: p.data.persistenceStatus ?? null,
            })
            setLastRealtimeAt(Date.now())
            setDataSource(p.data.source || 'ws')
          }
        } catch (err) {
          console.error('[WS] /topic/load 解析失败', err, msg.body)
        }
      })

      // 2. /topic/alerts
      client.subscribe('/topic/alerts', (msg) => {
        try {
          const p: WsAlertPayload = JSON.parse(msg.body)
          if (p.type === 'alert') {
            showAlertNotification(p.data)
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

      // 3. /topic/predictions
      client.subscribe('/topic/predictions', (msg) => {
        try {
          const p: WsPredictionPayload = JSON.parse(msg.body)
          if (p.type === 'prediction_update') {
            setForecast({
              predictions: p.data.predictions,
              model: p.data.model,
              forecastStartTime: p.data.forecastStartTime ?? null,
              lowerBounds: p.data.lowerBounds ?? null,
              upperBounds: p.data.upperBounds ?? null,
              intervalSource: p.data.intervalSource ?? null,
              modelVersionId: p.data.modelVersionId ?? null,
              futureWeatherAvailable: p.data.futureWeatherAvailable ?? false,
              weatherSource: p.data.weatherSource ?? null,
              futureWeatherApplied: p.data.futureWeatherApplied ?? false,
              futureWeatherFallback: p.data.futureWeatherFallback ?? false,
            })
          }
        } catch (err) {
          console.error('[WS] /topic/predictions 解析失败', err, msg.body)
        }
      })

      // 4. /topic/tickets — 工单更新（更新已有对象，不重复追加）
      client.subscribe('/topic/tickets', (msg) => {
        try {
          const p = JSON.parse(msg.body)
          if (p.type && p.data) {
            const cache = ticketCacheRef.current
            // 按 id 更新缓存
            if (p.data.id) {
              const existing = cache.get(p.data.id)
              cache.set(p.data.id, { ...existing, ...p.data })
            }
            // 触发全局事件让页面刷新
            window.dispatchEvent(new CustomEvent('ws:ticket-update', {
              detail: { type: p.type, data: p.data },
            }))
          }
        } catch (err) {
          console.error('[WS] /topic/tickets 解析失败', err, msg.body)
        }
      })

      // 5. /topic/alert-advice — AI 建议更新（更新已有对象）
      client.subscribe('/topic/alert-advice', (msg) => {
        try {
          const p = JSON.parse(msg.body)
          if (p.type && p.data) {
            const cache = adviceCacheRef.current
            if (p.data.id) {
              const existing = cache.get(p.data.id)
              cache.set(p.data.id, { ...existing, ...p.data })
            }
            window.dispatchEvent(new CustomEvent('ws:advice-update', {
              detail: { type: p.type, data: p.data },
            }))
          }
        } catch (err) {
          console.error('[WS] /topic/alert-advice 解析失败', err, msg.body)
        }
      })

      // 连接后拉取快照补偿缺口
      fetchAndMerge()
    }

    client.onDisconnect = () => {
      setWsConnected(false)
      setWsState('DISCONNECTED')
      console.log('[WS] disconnected')
    }

    client.onStompError = (f) => {
      console.error('[WS] STOMP error', f)
      setWsState('DISCONNECTED')
    }

    client.activate()
    clientRef.current = client
  }, [appendRealtime, appendAlert, setForecast, fetchAndMerge, setWsConnected, setWsState, setLastRealtimeAt, setDataSource])

  const disconnect = useCallback(() => {
    clientRef.current?.deactivate()
    clientRef.current = null
    setWsConnected(false)
    setWsState('DISCONNECTED')
  }, [setWsConnected, setWsState])

  // 挂载连接
  useEffect(() => {
    connect()
    return () => disconnect()
  }, [connect, disconnect])

  // 监听 token 变化 → 重连
  useEffect(() => {
    const unsub = useAuthStore.subscribe((state, prev) => {
      if (state.accessToken !== prev.accessToken && state.accessToken) {
        disconnect()
        setTimeout(() => connect(), 100)
      }
    })
    return unsub
  }, [connect, disconnect])

  // 监听退出登录 → 断开
  useEffect(() => {
    const unsub = useAuthStore.subscribe((state) => {
      if (!state.accessToken) {
        disconnect()
      }
    })
    return unsub
  }, [disconnect])

  // 监听手动重连事件
  useEffect(() => {
    const handler = () => {
      disconnect()
      setTimeout(() => connect(), 200)
    }
    window.addEventListener('ws:reconnect', handler)
    return () => window.removeEventListener('ws:reconnect', handler)
  }, [connect, disconnect])

  return { reconnect: connect, disconnect }
}
