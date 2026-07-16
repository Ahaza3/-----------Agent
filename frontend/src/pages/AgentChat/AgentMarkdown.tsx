/**
 * AgentMarkdown — 安全 Markdown 渲染，CRT 工业风格
 *
 * <p>仅对助手消息启用；禁止原始 HTML，XSS 安全。
 * 使用 react-markdown + remark-gfm 支持表格。</p>
 */
import { memo } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { Components } from 'react-markdown'

/* ─── 自定义渲染器 ─── */

const components: Components = {
  /* 标题 */
  h2: ({ children }) => <h2 className="md-h2">{children}</h2>,
  h3: ({ children }) => <h3 className="md-h3">{children}</h3>,

  /* 段落 */
  p: ({ children }) => <p className="md-p">{children}</p>,

  /* 强调 */
  strong: ({ children }) => <strong className="md-strong">{children}</strong>,
  em: ({ children }) => <em className="md-em">{children}</em>,

  /* 列表 */
  ul: ({ children }) => <ul className="md-ul">{children}</ul>,
  ol: ({ children }) => <ol className="md-ol">{children}</ol>,
  li: ({ children }) => <li className="md-li">{children}</li>,

  /* 行内代码 */
  code: ({ className, children, ...props }) => {
    const match = /language-(\w+)/.exec(className || '')
    const inline = !match && (props as { inline?: boolean }).inline !== false
      && (typeof children === 'string' && !children.includes('\n'))
    if (inline) {
      return <code className="md-code-inline">{children}</code>
    }
    return (
      <pre className="md-code-block">
        <code className={className}>{children}</code>
      </pre>
    )
  },

  /* 表格 */
  table: ({ children }) => (
    <div className="md-table-wrap">
      <table className="md-table">{children}</table>
    </div>
  ),
  thead: ({ children }) => <thead className="md-thead">{children}</thead>,
  tbody: ({ children }) => <tbody className="md-tbody">{children}</tbody>,
  tr: ({ children }) => <tr className="md-tr">{children}</tr>,
  th: ({ children }) => <th className="md-th">{children}</th>,
  td: ({ children }) => <td className="md-td">{children}</td>,

  /* 引用 */
  blockquote: ({ children }) => <blockquote className="md-blockquote">{children}</blockquote>,

  /* 链接 */
  a: ({ href, children }) => (
    <a className="md-link" href={href} target="_blank" rel="noopener noreferrer">
      {children}
    </a>
  ),

  /* 分隔线 */
  hr: () => <hr className="md-hr" />,
}

const AgentMarkdown = memo(({ content }: { content: string }) => (
  <div className="agent-markdown">
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      components={components}
      allowedElements={[
        'h2','h3','p','strong','em','ul','ol','li',
        'code','pre','table','thead','tbody','tr','th','td',
        'blockquote','a','hr','br','del',
      ]}
      unwrapDisallowed
    >
      {content}
    </ReactMarkdown>
  </div>
))

AgentMarkdown.displayName = 'AgentMarkdown'

export default AgentMarkdown
