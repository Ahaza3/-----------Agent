import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Button,
  Drawer,
  Empty,
  Input,
  Popconfirm,
  Skeleton,
  Tag,
  Tooltip,
  message,
} from 'antd'
import {
  DeleteOutlined,
  HistoryOutlined,
  MenuOutlined,
  PlusOutlined,
  RobotOutlined,
  SendOutlined,
  StopOutlined,
  UserOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import type { EChartsOption } from 'echarts'
import type { TextAreaRef } from 'antd/es/input/TextArea'
import LoadChart from '../../components/LoadChart'
import AgentMarkdown from './AgentMarkdown'
import {
  agentChat,
  deleteConversation,
  fetchConversationMessages,
  fetchConversations,
  type AgentEvent,
  type ConversationSummary,
} from '../../services/agentApi'
import useAuthStore from '../../stores/useAuthStore'
import { ROLE_CONFIG } from '../../config/roles'
import type { Role } from '../../config/roles'
import './index.css'

const ROLE_FOCUS: Record<Role, string> = {
  DISPATCHER: '运行风险、负荷趋势和调度建议',
  OPERATOR: '数据源、模型服务、规则配置、设备和系统核查',
  SYSTEM_ADMIN: '用户权限、系统健康、审计记录',
}

interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'error'
  content: string
  createdAt: string
  chart?: EChartsOption
  pending?: boolean
}

const QUICK_QUESTIONS = [
  '当前负荷是多少？',
  '最近24小时最高负荷是多少？',
  '最近24小时负荷趋势如何？',
]

function createMessageId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

const AgentChat = () => {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [thinking, setThinking] = useState('')
  const [conversationId, setConversationId] = useState<string>()
  const [conversations, setConversations] = useState<ConversationSummary[]>([])
  const [historyLoading, setHistoryLoading] = useState(true)
  const [conversationLoading, setConversationLoading] = useState(false)
  const [historyError, setHistoryError] = useState('')
  const [historyOpen, setHistoryOpen] = useState(false)

  const abortRef = useRef<AbortController | null>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<TextAreaRef>(null)
  const followOutputRef = useRef(true)

  const refreshConversations = useCallback(async () => {
    try {
      setHistoryError('')
      const data = await fetchConversations()
      setConversations(data)
    } catch {
      setHistoryError('历史会话加载失败')
    } finally {
      setHistoryLoading(false)
    }
  }, [])

  useEffect(() => {
    refreshConversations()
  }, [refreshConversations])

  useEffect(() => {
    const node = listRef.current
    if (node && followOutputRef.current) {
      node.scrollTo({ top: node.scrollHeight, behavior: 'auto' })
    }
  }, [messages, thinking])

  useEffect(() => () => abortRef.current?.abort(), [])

  const userRole = useAuthStore((s) => s.user?.role) as Role | undefined
  const IdentityBanner = () => (
    <div className="agent-identity-banner">
      <Tag color="blue">
        {userRole ? ROLE_CONFIG[userRole]?.label : '当前用户'}
      </Tag>
      <span>
        {userRole ? ROLE_FOCUS[userRole] || '通用运行分析' : '通用运行分析'}
      </span>
    </div>
  )

  const stopResponse = useCallback(() => {
    abortRef.current?.abort()
    abortRef.current = null
    setLoading(false)
    setThinking('')
    setMessages((previous) => previous.map((item) =>
      item.pending ? { ...item, pending: false, content: item.content || '回答已停止。' } : item,
    ))
    window.setTimeout(() => inputRef.current?.focus(), 0)
  }, [])

  const startNewConversation = useCallback(() => {
    if (loading) stopResponse()
    followOutputRef.current = true
    setConversationId(undefined)
    setMessages([])
    setThinking('')
    setHistoryOpen(false)
    window.setTimeout(() => inputRef.current?.focus(), 0)
  }, [loading, stopResponse])

  const openConversation = useCallback(async (summary: ConversationSummary) => {
    if (loading) stopResponse()
    followOutputRef.current = true
    setConversationLoading(true)
    setHistoryOpen(false)
    try {
      const history = await fetchConversationMessages(summary.conversationId)
      setConversationId(summary.conversationId)
      setMessages(history.map((item) => ({
        id: `history-${item.id}`,
        role: item.role,
        content: item.content,
        createdAt: item.createdAt,
      })))
      setThinking('')
    } catch {
      message.error('会话内容加载失败')
    } finally {
      setConversationLoading(false)
      window.setTimeout(() => inputRef.current?.focus(), 0)
    }
  }, [loading, stopResponse])

  const removeConversation = useCallback(async (id: string) => {
    try {
      await deleteConversation(id)
      setConversations((previous) => previous.filter((item) => item.conversationId !== id))
      if (conversationId === id) startNewConversation()
      message.success('会话已删除')
    } catch {
      message.error('删除会话失败')
    }
  }, [conversationId, startNewConversation])

  const sendMessage = useCallback((text: string) => {
    const value = text.trim()
    if (!value || loading) return

    followOutputRef.current = true
    const userMessage: ChatMessage = {
      id: createMessageId(),
      role: 'user',
      content: value,
      createdAt: new Date().toISOString(),
    }
    const assistantId = createMessageId()
    setMessages((previous) => [
      ...previous,
      userMessage,
      {
        id: assistantId,
        role: 'assistant',
        content: '',
        createdAt: new Date().toISOString(),
        pending: true,
      },
    ])
    setInput('')
    setLoading(true)
    setThinking('正在分析问题')

    const controller = agentChat(
      value,
      conversationId,
      (event: AgentEvent) => {
        if (event.type === 'thinking') {
          setThinking(event.content || '正在查询数据')
          return
        }
        if (event.type === 'text') {
          setThinking('')
          setMessages((previous) => previous.map((item) =>
            item.id === assistantId
              ? { ...item, content: item.content + (event.content || ''), pending: false }
              : item,
          ))
          return
        }
        if (event.type === 'chart' && event.option) {
          setMessages((previous) => previous.map((item) =>
            item.id === assistantId ? { ...item, chart: event.option } : item,
          ))
          return
        }
        if (event.type === 'error') {
          setThinking('')
          setMessages((previous) => previous.map((item) =>
            item.id === assistantId
              ? {
                  ...item,
                  role: 'error',
                  content: event.message || '请求失败，请稍后重试。',
                  pending: false,
                }
              : item,
          ))
          return
        }
        if (event.type === 'done') {
          setThinking('')
          if (event.conversationId) setConversationId(event.conversationId)
          setMessages((previous) => previous.map((item) =>
            item.id === assistantId ? { ...item, pending: false } : item,
          ))
        }
      },
      () => {
        setLoading(false)
        setThinking('')
        abortRef.current = null
        refreshConversations()
        window.setTimeout(() => inputRef.current?.focus(), 0)
      },
    )
    abortRef.current = controller
  }, [conversationId, loading, refreshConversations])

  const handleKeyDown = useCallback((event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      if (!loading) sendMessage(input)
    }
  }, [input, loading, sendMessage])

  const handleMessageScroll = useCallback(() => {
    const node = listRef.current
    if (!node) return

    const distanceFromBottom = node.scrollHeight - node.scrollTop - node.clientHeight
    followOutputRef.current = distanceFromBottom < 96
  }, [])

  const historyContent = useMemo(() => (
    <div className="agent-history-list">
      {historyLoading && <Skeleton active paragraph={{ rows: 5 }} title={false} />}
      {!historyLoading && historyError && (
        <div className="agent-history-error">
          <span>{historyError}</span>
          <Button type="link" size="small" onClick={refreshConversations}>重试</Button>
        </div>
      )}
      {!historyLoading && !historyError && conversations.length === 0 && (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无历史会话" />
      )}
      {conversations.map((item) => (
        <button
          type="button"
          key={item.conversationId}
          className={`agent-history-item ${conversationId === item.conversationId ? 'is-active' : ''}`}
          onClick={() => openConversation(item)}
        >
          <span className="agent-history-copy">
            <strong>{item.title || '新对话'}</strong>
            <small>{dayjs(item.updatedAt).format('MM-DD HH:mm')} · {item.messageCount} 条</small>
          </span>
          <Popconfirm
            title="删除这段会话？"
            description="删除后无法恢复。"
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={(event) => {
              event?.stopPropagation()
              removeConversation(item.conversationId)
            }}
          >
            <Tooltip title="删除会话">
              <span
                role="button"
                tabIndex={0}
                className="agent-history-delete"
                onClick={(event) => event.stopPropagation()}
                onKeyDown={(event) => event.stopPropagation()}
              >
                <DeleteOutlined />
              </span>
            </Tooltip>
          </Popconfirm>
        </button>
      ))}
    </div>
  ), [
    conversationId,
    conversations,
    historyError,
    historyLoading,
    openConversation,
    refreshConversations,
    removeConversation,
  ])

  return (
    <div className="agent-shell">
      <aside className="agent-history-panel">
        <div className="agent-history-header">
          <div>
            <HistoryOutlined />
            <span>历史对话</span>
          </div>
          <Tooltip title="新建对话">
            <Button type="text" icon={<PlusOutlined />} onClick={startNewConversation} />
          </Tooltip>
        </div>
        {historyContent}
      </aside>

      <main className="agent-workspace">
        <header className="agent-chat-header">
          <div className="agent-chat-heading">
            <Button
              className="agent-history-mobile"
              type="text"
              icon={<MenuOutlined />}
              onClick={() => setHistoryOpen(true)}
            />
            <div className="agent-title-icon"><RobotOutlined /></div>
            <div>
              <h1>智能负荷助手</h1>
              <p>负荷查询、趋势分析、告警处置建议</p>
            </div>
          </div>
          <div className="agent-session-meta">
            <span>{conversationId ? conversationId.slice(0, 8).toUpperCase() : '新会话'}</span>
          </div>
        </header>

        {/* 角色身份提示 */}
        <IdentityBanner />

        <section
          ref={listRef}
          className="agent-message-list"
          aria-live="polite"
          onScroll={handleMessageScroll}
        >
          {conversationLoading && (
            <div className="agent-conversation-loading"><Skeleton active paragraph={{ rows: 5 }} /></div>
          )}

          {!conversationLoading && messages.length === 0 && (
            <div className="agent-empty-state">
              <div className="agent-empty-mark"><RobotOutlined /></div>
              <h2>从电力数据开始提问</h2>
              <p>助手会结合实时负荷、历史统计、预测结果和告警上下文回答。</p>
              <div className="agent-quick-grid">
                {QUICK_QUESTIONS.map((question) => (
                  <button type="button" key={question} onClick={() => sendMessage(question)}>
                    <span>↗</span>{question}
                  </button>
                ))}
              </div>
            </div>
          )}

          {!conversationLoading && messages.map((item) => (
            <article key={item.id} className={`agent-message-row is-${item.role}`}>
              <div className="agent-message-avatar">
                {item.role === 'user' ? <UserOutlined /> : <RobotOutlined />}
              </div>
              <div className="agent-message-body">
                <div className="agent-message-meta">
                  <strong>{item.role === 'user' ? '你' : item.role === 'error' ? '请求异常' : '智能助手'}</strong>
                  <time>{dayjs(item.createdAt).format('HH:mm')}</time>
                </div>
                <div className="agent-message-content" style={item.role === 'assistant' ? { whiteSpace: 'normal' } : undefined}>
                  {item.role === 'assistant' && item.content
                    ? <AgentMarkdown content={item.content} />
                    : (item.content || (item.pending ? '正在准备回答…' : ''))
                  }
                </div>
                {item.chart && (
                  <div className="agent-message-chart">
                    <LoadChart option={item.chart} height={280} />
                  </div>
                )}
              </div>
            </article>
          ))}

          {thinking && (
            <div className="agent-thinking">
              <span /><span /><span />
              <strong>{thinking}</strong>
            </div>
          )}
        </section>

        <footer className="agent-composer">
          <div className="agent-composer-box">
            <Input.TextArea
              ref={inputRef}
              value={input}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="输入负荷、趋势或告警相关问题…"
              maxLength={2000}
              autoSize={{ minRows: 2, maxRows: 6 }}
              autoFocus
            />
            <div className="agent-composer-actions">
              <span>{input.length}/2000</span>
              {loading ? (
                <Button danger icon={<StopOutlined />} onClick={stopResponse}>停止</Button>
              ) : (
                <Button
                  type="primary"
                  icon={<SendOutlined />}
                  disabled={!input.trim()}
                  onClick={() => sendMessage(input)}
                >
                  发送
                </Button>
              )}
            </div>
          </div>
        </footer>
      </main>

      <Drawer
        title="历史对话"
        placement="left"
        width="min(88vw, 320px)"
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
        extra={<Button type="text" icon={<PlusOutlined />} onClick={startNewConversation} />}
      >
        {historyContent}
      </Drawer>
    </div>
  )
}

export default AgentChat
