/**
 * StatCard — Brutalist 统计卡片
 * 可见边框 / 零圆角 / monospace 数值
 */
import { Card, Statistic } from 'antd'

export interface StatCardProps {
  title: string
  value: string
  suffix?: string
  color?: string
  sub?: string
}

const StatCard = ({
  title,
  value,
  suffix,
  color = '#EAEAEA',
  sub,
}: StatCardProps) => {
  return (
    <Card
      size="small"
      styles={{ body: { padding: '10px 14px' } }}
      style={{ border: '1px solid #2A2A2A' }}
    >
      {/* 标题 */}
      <div
        className="font-mono"
        style={{
          color: '#888888',
          fontSize: 10,
          letterSpacing: '0.06em',
          marginBottom: 2,
        }}
      >
        {title}
      </div>

      {/* 数值 */}
      <Statistic
        value={value}
        suffix={
          suffix ? (
            <span className="font-mono" style={{ fontSize: 12, color: '#666666' }}>
              {suffix}
            </span>
          ) : undefined
        }
        valueStyle={{
          color,
          fontSize: 22,
          fontWeight: 700,
          fontFamily:
            "'JetBrains Mono', 'IBM Plex Mono', 'SF Mono', 'Consolas', monospace",
        }}
      />

      {/* 底部辅助信息 */}
      {sub && (
        <div
          className="font-mono"
          style={{ color: '#666666', fontSize: 10, marginTop: 2, letterSpacing: '0.04em' }}
        >
          {sub}
        </div>
      )}
    </Card>
  )
}

export default StatCard
