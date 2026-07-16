/**
 * Agent SSE API 单元测试 — 事件解析、流处理、AbortController
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { agentChat, type AgentEvent } from '../services/agentApi'

/** 创建模拟 SSE 响应流 — 按完整事件边界发送，避免跨块断行 */
function mockSseStream(
  events: Array<{ event: string; data: object }>,
): Response {
  const encoder = new TextEncoder()
  const chunks: Uint8Array[] = []
  for (const e of events) {
    const block = `event: ${e.event}\ndata: ${JSON.stringify(e.data)}\n\n`
    chunks.push(encoder.encode(block))
  }

  const stream = new ReadableStream({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(chunk)
      }
      controller.close()
    },
  })

  return new Response(stream, {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' },
  })
}

describe('agentApi — SSE 事件解析', () => {
  let fetchSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, 'fetch')
  })

  afterEach(() => {
    fetchSpy.mockRestore()
  })

  /* ─── text 事件分段拼接 ─── */

  it('应该正确解析 text 事件并拼接', async () => {
    const events: AgentEvent[] = []

    fetchSpy.mockResolvedValue(
      mockSseStream([
        { event: 'thinking', data: { content: '正在查询…' } },
        { event: 'text', data: { content: '当前负荷' } },
        { event: 'text', data: { content: ' 940.5 MW（模拟数据）' } },
        { event: 'done', data: { conversationId: 'abc123' } },
      ]),
    )

    await new Promise<void>((resolve) => {
      agentChat('test', undefined, (e) => events.push(e), resolve)
    })

    expect(events.length).toBe(4)
    expect(events[0]).toEqual({ type: 'thinking', content: '正在查询…' })
    expect(events[1]).toEqual({ type: 'text', content: '当前负荷' })
    expect(events[2]).toEqual({ type: 'text', content: ' 940.5 MW（模拟数据）' })
    expect(events[3]).toEqual({ type: 'done', conversationId: 'abc123' })
  })

  it('event 和 data 跨网络分片时仍应保留事件类型', async () => {
    const events: AgentEvent[] = []
    const encoder = new TextEncoder()
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('event: text\n'))
        controller.enqueue(encoder.encode('data: {"content":"跨分片消息"}\n\n'))
        controller.enqueue(encoder.encode('event: done\n'))
        controller.enqueue(encoder.encode('data: {"conversationId":"split-id"}\n\n'))
        controller.close()
      },
    })
    fetchSpy.mockResolvedValue(new Response(stream, { status: 200 }))

    await new Promise<void>((resolve) => {
      agentChat('test', undefined, (event) => events.push(event), resolve)
    })

    expect(events).toEqual([
      { type: 'text', content: '跨分片消息' },
      { type: 'done', conversationId: 'split-id' },
    ])
  })

  /* ─── chart 事件 ─── */

  it('应该正确解析 chart 事件', async () => {
    const events: AgentEvent[] = []
    const chartOption = { xAxis: { type: 'category' }, series: [{ data: [1, 2, 3] }] }

    fetchSpy.mockResolvedValue(
      mockSseStream([
        { event: 'text', data: { content: '趋势图如下：' } },
        { event: 'chart', data: { option: chartOption } },
        { event: 'done', data: { conversationId: 'xyz' } },
      ]),
    )

    await new Promise<void>((resolve) => {
      agentChat('test', undefined, (e) => events.push(e), resolve)
    })

    expect(events[1].type).toBe('chart')
    expect(events[1].option).toEqual(chartOption)
  })

  /* ─── error 事件 ─── */

  it('应该正确处理 error 事件', async () => {
    const events: AgentEvent[] = []

    fetchSpy.mockResolvedValue(
      mockSseStream([
        { event: 'error', data: { message: 'LLM API Key 未配置' } },
        { event: 'done', data: { conversationId: '' } },
      ]),
    )

    await new Promise<void>((resolve) => {
      agentChat('test', undefined, (e) => events.push(e), resolve)
    })

    expect(events[0].type).toBe('error')
    expect(events[0].message).toContain('LLM API Key')
  })

  /* ─── done 事件后 complete 回调 ─── */

  it('应该在线结束后调用 complete 回调', async () => {
    let completed = false

    fetchSpy.mockResolvedValue(
      mockSseStream([
        { event: 'text', data: { content: 'ok' } },
        { event: 'done', data: { conversationId: 'done-id' } },
      ]),
    )

    await new Promise<void>((resolve) => {
      agentChat(
        'test',
        undefined,
        () => {},
        () => {
          completed = true
          resolve()
        },
      )
    })

    expect(completed).toBe(true)
  })

  /* ─── HTTP 错误处理 ─── */

  it('应该在 HTTP 非 2xx 时触发 error', async () => {
    const events: AgentEvent[] = []

    fetchSpy.mockResolvedValue(
      new Response('Internal Server Error', { status: 500 }),
    )

    await new Promise<void>((resolve) => {
      agentChat('test', undefined, (e) => events.push(e), resolve)
    })

    expect(events[0].type).toBe('error')
    expect(events[0].message).toContain('500')
  })

  /* ─── AbortController 停止 ─── */

  it('应该支持 AbortController 取消请求', async () => {
    const events: AgentEvent[] = []
    let aborted = false

    // 模拟一个永远不完成的 fetch
    fetchSpy.mockImplementation((_url, options) => {
      const signal = (options as RequestInit)?.signal
      return new Promise((_resolve, reject) => {
        if (signal) {
          signal.addEventListener('abort', () => {
            aborted = true
            reject(new DOMException('Aborted', 'AbortError'))
          })
        }
      })
    })

    const ctrl = agentChat(
      'test',
      undefined,
      (e) => events.push(e),
      () => {},
    )

    // 等待 tick 后中止
    await new Promise((r) => setTimeout(r, 10))
    ctrl.abort()

    // 等待 abort 传播
    await new Promise((r) => setTimeout(r, 50))

    expect(aborted).toBe(true)
    // 中止不应产生 error 事件
    expect(events.filter((e) => e.type === 'error').length).toBe(0)
  })

  /* ─── 网络错误处理 ─── */

  it('应该处理网络错误为 error 事件', async () => {
    const events: AgentEvent[] = []

    fetchSpy.mockRejectedValue(new Error('Network Error'))

    await new Promise<void>((resolve) => {
      agentChat('test', undefined, (e) => events.push(e), resolve)
    })

    expect(events[0].type).toBe('error')
    expect(events[0].message).toContain('Network Error')
  })

  /* ─── conversationId 传递 ─── */

  it('应该在请求中传递 conversationId', async () => {
    const events: AgentEvent[] = []

    fetchSpy.mockResolvedValue(
      mockSseStream([
        { event: 'text', data: { content: 'hello' } },
        { event: 'done', data: { conversationId: 'existing-id' } },
      ]),
    )

    await new Promise<void>((resolve) => {
      agentChat('test', 'my-conv-123', (e) => events.push(e), resolve)
    })

    // 验证 fetch 被调用时携带了正确的 body
    const callBody = JSON.parse((fetchSpy.mock.calls[0]?.[1] as RequestInit)?.body as string)
    expect(callBody.conversationId).toBe('my-conv-123')
  })

  /* ─── 忽略格式错误的 data 行 ─── */

  it('应该跳过无法解析的 JSON 数据行', async () => {
    const events: AgentEvent[] = []

    // 手动构造包含无效 JSON 的流
    const encoder = new TextEncoder()
    const body =
      'event: text\ndata: {"content":"valid"}\n\n' +
      'event: text\ndata: {invalid json}\n\n' +
      'event: text\ndata: {"content":"also valid"}\n\n' +
      'event: done\ndata: {"conversationId":"x"}\n\n'

    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(body))
        controller.close()
      },
    })

    fetchSpy.mockResolvedValue(
      new Response(stream, { status: 200 }),
    )

    await new Promise<void>((resolve) => {
      agentChat('test', undefined, (e) => events.push(e), resolve)
    })

    // 应该只包含有效的事件
    expect(events.filter((e) => e.type === 'text').length).toBe(2)
    expect(events.filter((e) => e.type === 'done').length).toBe(1)
  })
})
