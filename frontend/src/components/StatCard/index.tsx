/**
 * StatCard — 可复用统计卡片
 * 峰值 / 谷值 / 平均值 / 负荷率 统一展示
 */
import { Card, Statistic } from 'antd'
import type { ReactNode } from 'react'

export interface StatCardProps {
  /** 卡片标题 */
  title: string
  /** 统计值（字符串，支持 "--" 等占位） */
  value: string
  /** 数值后缀，如 " MW" / " %" */
  suffix?: string
  /** 数值颜色 */
  color?: string
  /** 卡片图标 */
  icon?: ReactNode
  /** 底部辅助信息 */
  sub?: string
  /** 卡片宽度自适应，默认 true */
  fluid?: boolean
}

const StatCard = ({
  title,
  value,
  suffix,
  color = '#4f8cff',
  icon,
  sub,
}: StatCardProps) => {
  return (
    <Card size="small" bodyStyle={{ padding: '16px 20px' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
        }}
      >
        <div style={{ flex: 1, minWidth: 0 }}>
          <div
            style={{
              color: '#8892a4',
              fontSize: 13,
              marginBottom: 4,
            }}
          >
            {title}
          </div>
          <Statistic
            value={value}
            suffix={
              suffix ? (
                <span style={{ fontSize: 14, color: '#5c6680' }}>{suffix}</span>
              ) : undefined
            }
            valueStyle={{
              color,
              fontSize: 28,
              fontWeight: 700,
              fontFamily: "'DIN Alternate', 'Helvetica Neue', sans-serif",
            }}
          />
          {sub && (
            <div style={{ color: '#5c6680', fontSize: 12, marginTop: 4 }}>
              {icon && <span style={{ marginRight: 4 }}>{icon}</span>}
              {sub}
            </div>
          )}
        </div>
      </div>
    </Card>
  )
}

export default StatCard
