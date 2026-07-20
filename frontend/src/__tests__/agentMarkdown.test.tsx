/**
 * AgentMarkdown 单元测试 — 渲染正确性与 XSS 安全
 */
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import AgentMarkdown from '../pages/AgentChat/AgentMarkdown'

describe('AgentMarkdown — Markdown 渲染', () => {

  it('应该渲染 bold 文本（不暴露星号）', () => {
    render(<AgentMarkdown content="当前负荷 **940.2 MW**" />)
    const strong = screen.getByText('940.2 MW')
    expect(strong.tagName).toBe('STRONG')
    expect(document.querySelector('.agent-markdown')!.textContent).not.toContain('**')
  })

  it('应该渲染 GFM 表格', () => {
    const md = '| 时间 | 负荷 |\n|------|------|\n| 08:00 | 850 MW |\n| 12:00 | 980 MW |'
    render(<AgentMarkdown content={md} />)
    expect(document.querySelector('table')).toBeTruthy()
    expect(document.querySelector('th')).toBeTruthy()
    expect(screen.getByText('850 MW')).toBeTruthy()
  })

  it('应该渲染标题（h2）', () => {
    const md = '## 负荷分析\n\n下面是具体数据。'
    render(<AgentMarkdown content={md} />)
    const h2 = screen.getByText(/负荷分析/)
    expect(h2.tagName).toBe('H2')
  })

  it('应该渲染列表', () => {
    const md = '- 当前负荷：940 MW\n- 峰值：1100 MW'
    render(<AgentMarkdown content={md} />)
    expect(document.querySelector('ul')).toBeTruthy()
    expect(document.querySelectorAll('li').length).toBe(2)
  })

  it('应该渲染引用块', () => {
    render(<AgentMarkdown content="> 这是数据说明" />)
    expect(document.querySelector('blockquote')).toBeTruthy()
    expect(screen.getByText('这是数据说明')).toBeTruthy()
  })

  it('应该渲染行内代码', () => {
    render(<AgentMarkdown content="模型 `LSTM_v2` 运行中" />)
    const code = screen.getByText('LSTM_v2')
    expect(code.tagName).toBe('CODE')
  })

  it('不应该执行 HTML 标签（script/b 等）', () => {
    render(<AgentMarkdown content={'<script>alert("xss")</script><b>bold</b>'} />)
    // HTML 标签不应出现在 DOM 中
    expect(document.querySelector('script')).toBeNull()
    expect(document.querySelector('b')).toBeNull()
    expect(document.querySelector('.agent-markdown')).toBeTruthy()
  })

  it('不应将用户输入的 div 标签渲染为 DOM 元素', () => {
    render(<AgentMarkdown content="用户输入了 **bold** 和 <div>不应该出现</div>" />)
    // .agent-markdown 内部不应出现 <div> 子元素
    const root = document.querySelector('.agent-markdown')!
    expect(root.querySelector('div')).toBeNull()
    // 但 bold 应该被渲染
    expect(root.querySelector('strong')).toBeTruthy()
  })

  it('流式不完整 Markdown 不应报错', () => {
    expect(() => {
      render(<AgentMarkdown content="| 时间 | 负荷 |\n|------|-----" />)
    }).not.toThrow()
  })

  it('完整表格应创建横向滚动容器', () => {
    const md = '| A | B | C | D |\n|---|---|---|---|\n| 1 | 2 | 3 | 4 |'
    render(<AgentMarkdown content={md} />)
    const wrap = document.querySelector('.md-table-wrap')
    expect(wrap).toBeTruthy()
  })
})
