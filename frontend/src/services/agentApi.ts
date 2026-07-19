/**
 * Agent 对话 API — SSE 流式调用
 */
import type { EChartsOption } from 'echarts'
import api from './api'
import useAuthStore from '../stores/useAuthStore'

export interface AgentEvent {
  type: 'thinking' | 'text' | 'chart' | 'done' | 'error'
  content?: string
  option?: EChartsOption
  conversationId?: string
  message?: string
}

export interface ConversationSummary {
  conversationId: string
  title: string
  lastMessage: string
  messageCount: number
  updatedAt: string
}

export interface ConversationMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  chart?: EChartsOption
  chartOption?: string | null
  createdAt: string
}

export function fetchConversations(limit = 50): Promise<ConversationSummary[]> {
  return api.get('/agent/conversations', { params: { limit } })
}

export function fetchConversationMessages(
  conversationId: string,
): Promise<ConversationMessage[]> {
  return api.get(`/agent/conversations/${conversationId}`)
}

export function deleteConversation(conversationId: string): Promise<void> {
  return api.delete(`/agent/conversations/${conversationId}`)
}

/**
 * 发起 Agent SSE 对话，返回 AbortController 用于取消。
 * callback 逐事件调用，complete 在流结束时调用。
 */
export function agentChat(
  message: string,
  conversationId: string | undefined,
  onEvent: (event: AgentEvent) => void,
  onComplete: () => void,
): AbortController {
  const controller = new AbortController()

  const token = useAuthStore.getState().accessToken
  fetch('/api/v1/agent/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({
      message,
      conversationId: conversationId || null,
    }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        onEvent({ type: 'error', message: `HTTP ${response.status}` })
        onComplete()
        return
      }

      const reader = response.body?.getReader()
      if (!reader) {
        onEvent({ type: 'error', message: '不支持流式读取' })
        onComplete()
        return
      }

      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (trimmed.startsWith('event:')) {
            currentEvent = trimmed.slice(6).trim()
          } else if (trimmed.startsWith('data:')) {
            const dataStr = trimmed.slice(5).trim()
            try {
              const data = JSON.parse(dataStr)
              const event: AgentEvent = {
                type: currentEvent as AgentEvent['type'],
                ...data,
              }
              onEvent(event)
            } catch {
              // Skip malformed JSON
            }
            currentEvent = ''
          }
        }
      }

      onComplete()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onEvent({ type: 'error', message: err.message || '网络错误' })
      }
      onComplete()
    })

  return controller
}
