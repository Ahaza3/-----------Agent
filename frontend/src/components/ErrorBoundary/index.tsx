import { Component } from 'react'
import { Result, Button, Space } from 'antd'
import { ReloadOutlined, HomeOutlined } from '@ant-design/icons'

interface Props {
  children: React.ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
}

/** 全局错误边界 — 页面渲染异常时提供重新加载和返回首页入口 */
class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, error: null }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  handleReload = () => {
    this.setState({ hasError: false, error: null })
    window.location.reload()
  }

  handleGoHome = () => {
    window.location.href = '/'
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          minHeight: '100vh', background: '#0a0a0a',
        }}>
          <Result
            status="error"
            title={<span style={{ color: '#EAEAEA' }}>页面渲染异常</span>}
            subTitle={
              <span style={{ color: '#888' }}>
                {this.state.error?.message || '未知错误'}
              </span>
            }
            extra={
              <Space>
                <Button icon={<ReloadOutlined />} onClick={this.handleReload}>
                  重新加载
                </Button>
                <Button icon={<HomeOutlined />} onClick={this.handleGoHome}>
                  返回首页
                </Button>
              </Space>
            }
          />
        </div>
      )
    }
    return this.props.children
  }
}

export default ErrorBoundary
