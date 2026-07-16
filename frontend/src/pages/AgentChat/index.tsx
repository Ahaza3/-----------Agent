/**
 * Agent 对话 — CRT 终端风格 SSE 对话框
 * P0 · Day 10 交付
 */
import { useState, useRef, useCallback, useEffect } from 'react'
import { Button, Input, Space, Typography } from 'antd'
import LoadChart from '../../components/LoadChart'
import type { EChartsOption } from 'echarts'
import { agentChat, type AgentEvent } from '../../services/agentApi'

/* ─── 类型 ─── */

interface ChatMessage {
  id: number
  role: 'user' | 'assistant' | 'error'
  content: string
  chart?: EChartsOption
  thinking?: boolean
}

interface ChartBlock {
  id: number
  option: EChartsOption
}

/* ─── 快捷问题 ─── */

const QUICK_QUESTIONS = [
  '当前负荷是多少？',
  '最近24小时最高负荷是多少？',
  '最近24小时负荷趋势如何？',
]

/* ─── 样式常量 ─── */

const MESSAGE_STYLE: React.CSSProperties = {
  fontFamily: "'JetBrains Mono', 'IBM Plex Mono', 'Consolas', monospace",
  fontSize: 12,
  lineHeight: '1.7',
  whiteSpace: 'pre-wrap',
  wordBreak: 'break-word',
}

const USER_BUBBLE: React.CSSProperties = {
  ...MESSAGE_STYLE,
  color: '#4AF626',
  borderLeft: '2px solid #4AF626',
  paddingLeft: 12,
  marginBottom: 12,
}

const ASSISTANT_BUBBLE: React.CSSProperties = {
  ...MESSAGE_STYLE,
  color: '#EAEAEA',
  borderLeft: '2px solid #FF2A2A',
  paddingLeft: 12,
  marginBottom: 12,
}

const ERROR_BUBBLE: React.CSSProperties = {
  ...MESSAGE_STYLE,
  color: '#FF2A2A',
  borderLeft: '2px solid #FF2A2A',
  paddingLeft: 12,
  marginBottom: 12,
}

const THINKING_STYLE: React.CSSProperties = {
  ...MESSAGE_STYLE,
  color: '#FF2A2A',
  marginBottom: 8,
  opacity: 0.8,
}

let messageId = 0

/* ══════════════════════════════════════════════
 *  AgentChat 主组件
 * ══════════════════════════════════════════════ */

const AgentChat = () => {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [thinking, setThinking] = useState('')
  const [charts, setCharts] = useState<ChartBlock[]>([])
  const [conversationId, setConversationId] = useState<string>()

  const abortRef = useRef<AbortController | null>(null)
  const listRef = useRef<HTMLDivElement>(null)

  // 自动滚动
  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight
    }
  }, [messages, thinking])

  // 卸载时中止
  useEffect(() => {
    return () => {
      abortRef.current?.abort()
    }
  }, [])

  /* ─── 发送 ─── */

  const doSend = useCallback(
    (text: string) => {
      const trimmed = text.trim()
      if (!trimmed || loading) return

      // 添加用户消息
      const userMsg: ChatMessage = {
        id: ++messageId,
        role: 'user',
        content: trimmed,
      }
      setMessages((prev) => [...prev, userMsg])
      setInput('')
      setLoading(true)
      setThinking('正在连接…')

      // 当前 assistant 消息（SSE text 事件增量拼接）
      let assistantId = ++messageId
      setMessages((prev) => [
        ...prev,
        { id: assistantId, role: 'assistant', content: '', thinking: true },
      ])

      const ctrl = agentChat(
        trimmed,
        conversationId,
        (event: AgentEvent) => {
          switch (event.type) {
            case 'thinking':
              setThinking(event.content || '')
              break

            case 'text':
              setThinking('')
              setMessages((prev) =>
                prev.map((m) =>
                  m.id === assistantId
                    ? { ...m, content: m.content + event.content, thinking: false }
                    : m,
                ),
              )
              break

            case 'chart':
              if (event.option) {
                const chartId = ++messageId
                setCharts((prev) => [...prev, { id: chartId, option: event.option }])
              }
              break

            case 'error':
              setThinking('')
              setMessages((prev) =>
                prev.map((m) =>
                  m.id === assistantId
                    ? { ...m, role: 'error' as const, content: event.message || '未知错误', thinking: false }
                    : m,
                ),
              )
              break

            case 'done':
              setThinking('')
              if (event.conversationId) {
                setConversationId(event.conversationId)
              }
              setMessages((prev) =>
                prev.map((m) =>
                  m.id === assistantId
                    ? { ...m, thinking: false }
                    : m,
                ),
              )
              break
          }
        },
        () => {
          setLoading(false)
          setThinking('')
          abortRef.current = null
        },
      )

      abortRef.current = ctrl
    },
    [conversationId, loading],
  )

  /* ─── 停止 ─── */

  const doStop = useCallback(() => {
    abortRef.current?.abort()
    abortRef.current = null
    setLoading(false)
    setThinking('')
  }, [])

  /* ─── 快捷问题 ─── */

  const handleQuick = useCallback(
    (q: string) => doSend(q),
    [doSend],
  )

  /* ─── 键盘发送 ─── */

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault()
        doSend(input)
      }
    },
    [input, doSend],
  )

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 52px)' }}>
      {/* ═══ 标题栏 ═══ */}
      <div
        style={{
          padding: '12px 20px',
          borderBottom: '1px solid #2A2A2A',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Space>
          <span
            className="font-mono"
            style={{ fontSize: 13, color: '#AAAAAA', letterSpacing: '0.1em' }}
          >
            &gt;&gt; AGENT_CONSOLE &lt;&lt;
          </span>
        </Space>
        <Typography.Text
          className="font-mono"
          style={{ fontSize: 10, color: '#666666' }}
        >
          {conversationId
            ? `SESSION: ${conversationId.slice(0, 8).toUpperCase()}`
            : 'NEW SESSION'}
        </Typography.Text>
      </div>

      {/* ═══ 消息列表 ═══ */}
      <div
        ref={listRef}
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: '16px 20px',
          background: '#0A0A0A',
        }}
      >
        {messages.length === 0 && !loading && (
          <div style={{ textAlign: 'center', paddingTop: 60 }}>
            <p
              className="font-mono"
              style={{ color: '#666666', fontSize: 12, marginBottom: 24 }}
            >
              // 电力负荷监控与智能告警助手 //
            </p>
            <p style={{ color: '#888888', fontSize: 12, marginBottom: 16 }}>
              您可以询问当前负荷、历史趋势、统计数据和告警情况。
            </p>
            <Space wrap size={[8, 8]}>
              {QUICK_QUESTIONS.map((q) => (
                <Button
                  key={q}
                  size="small"
                  onClick={() => handleQuick(q)}
                  style={{
                    fontFamily: "'JetBrains Mono', 'Consolas', monospace",
                    fontSize: 11,
                    color: '#888888',
                    borderColor: '#2A2A2A',
                    background: '#0E0E0E',
                  }}
                >
                  {'>'} {q}
                </Button>
              ))}
            </Space>
          </div>
        )}

        {messages.map((m) => {
          if (m.role === 'assistant' && !m.content && m.thinking) return null // thinking 中的空消息不渲染
          return (
            <div
              key={m.id}
              style={
                m.role === 'user'
                  ? USER_BUBBLE
                  : m.role === 'error'
                    ? ERROR_BUBBLE
                    : ASSISTANT_BUBBLE
              }
            >
              <span
                style={{
                  fontSize: 10,
                  color: m.role === 'user' ? '#4AF626' : m.role === 'error' ? '#FF2A2A' : '#FF2A2A',
                  marginRight: 8,
                  opacity: 0.7,
                }}
              >
                {m.role === 'user' ? 'YOU>' : m.role === 'error' ? 'ERR>' : 'AI>'}
              </span>
              {m.content || (m.thinking ? '…' : '')}
            </div>
          )
        })}

        {thinking && (
          <div style={THINKING_STYLE}>
            <span style={{ fontSize: 10, marginRight: 8, opacity: 0.7 }}>SYS&gt;</span>
            {thinking}
            <span className="font-mono" style={{ animation: 'blink 1s step-end infinite' }}>▌</span>
          </div>
        )}

        {/* 图表 */}
        {charts.map((c) => (
          <div key={c.id} style={{ marginBottom: 16 }}>
            <LoadChart option={c.option} height={280} />
          </div>
        ))}
      </div>

      {/* ═══ 输入区 ═══ */}
      <div
        style={{
          padding: '12px 20px',
          borderTop: '1px solid #2A2A2A',
          background: '#0C0C0C',
          display: 'flex',
          gap: 8,
        }}
      >
        <Input.TextArea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入查询，按 Enter 发送…"
          maxLength={2000}
          autoSize={{ minRows: 1, maxRows: 4 }}
          disabled={loading}
          style={{
            background: '#0A0A0A',
            borderColor: '#2A2A2A',
            color: '#EAEAEA',
            fontFamily: "'JetBrains Mono', 'Consolas', monospace",
            fontSize: 12,
            resize: 'none',
          }}
        />
        {loading ? (
          <Button
            danger
            onClick={doStop}
            className="font-mono"
            style={{ height: 'auto', minHeight: 34, fontSize: 11 }}
          >
            ■ STOP
          </Button>
        ) : (
          <Button
            type="primary"
            onClick={() => doSend(input)}
            disabled={!input.trim()}
            className="font-mono"
            style={{ height: 'auto', minHeight: 34, fontSize: 11 }}
          >
            ▶ SEND
          </Button>
        )}
      </div>
    </div>
  )
}

export default AgentChat
