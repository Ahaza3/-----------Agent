/**
 * 数据查询 — Brutalist CRT Terminal 风格
 * P0 · Sprint 1
 */
import { useState, useEffect, useCallback, useMemo } from 'react'
import {
  Table,
  DatePicker,
  InputNumber,
  Button,
  Space,
  message,
  Segmented,
} from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import { fetchLoadRange } from '../../services/dataApi'
import type { LoadData } from '../../types/load'

const { RangePicker } = DatePicker
type QuickRange = '24h' | '7d' | '30d'

function quickToRange(q: QuickRange): [Dayjs, Dayjs] {
  const now = dayjs()
  switch (q) {
    case '24h':
      return [now.subtract(24, 'hour'), now]
    case '7d':
      return [now.subtract(7, 'day'), now]
    case '30d':
      return [now.subtract(30, 'day'), now]
  }
}

const COLUMNS: ColumnsType<LoadData> = [
  {
    title: '时间',
    dataIndex: 'time',
    key: 'time',
    width: 170,
    render: (v: string) => (
      <span className="font-mono" style={{ fontSize: 12 }}>
        {dayjs(v).format('YYYY-MM-DD HH:mm')}
      </span>
    ),
    sorter: (a, b) => dayjs(a.time).unix() - dayjs(b.time).unix(),
    defaultSortOrder: 'descend',
  },
  {
    title: '负荷 (MW)',
    dataIndex: 'loadMw',
    key: 'loadMw',
    width: 130,
    render: (v: number) => (
      <span className="font-mono" style={{ fontWeight: 700, fontSize: 13 }}>
        {v.toFixed(1)}
      </span>
    ),
    sorter: (a, b) => a.loadMw - b.loadMw,
  },
  {
    title: '温度 (°C)',
    dataIndex: 'temperature',
    key: 'temperature',
    width: 90,
    render: (v: number) => (
      <span className="font-mono" style={{ fontSize: 12 }}>{v.toFixed(1)}</span>
    ),
  },
  {
    title: '湿度 (%)',
    dataIndex: 'humidity',
    key: 'humidity',
    width: 80,
    render: (v: number) => (
      <span className="font-mono" style={{ fontSize: 12 }}>{v.toFixed(0)}</span>
    ),
  },
  {
    title: '节假日',
    dataIndex: 'isHoliday',
    key: 'isHoliday',
    width: 70,
    render: (v: 0 | 1) => (
      <span
        className="font-mono"
        style={{ color: v === 1 ? '#FF2A2A' : '#888888', fontSize: 11 }}
      >
        {v === 1 ? '是' : '否'}
      </span>
    ),
    filters: [
      { text: '是', value: 1 },
      { text: '否', value: 0 },
    ],
    onFilter: (value, record) => record.isHoliday === value,
  },
  {
    title: '小时',
    dataIndex: 'hour',
    key: 'hour',
    width: 60,
    render: (v: number) => (
      <span className="font-mono" style={{ fontSize: 12 }}>{v}</span>
    ),
  },
  {
    title: '星期',
    dataIndex: 'dayOfWeek',
    key: 'dayOfWeek',
    width: 60,
    render: (v: number) => (
      <span className="font-mono" style={{ fontSize: 12 }}>
        {['一', '二', '三', '四', '五', '六', '日'][v] ?? v}
      </span>
    ),
  },
]

const DataQuery = () => {
  const [quickRange, setQuickRange] = useState<QuickRange>('24h')
  const [customRange, setCustomRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [data, setData] = useState<LoadData[]>([])
  const [loading, setLoading] = useState(false)
  const [minLoad, setMinLoad] = useState<number | null>(null)
  const [maxLoad, setMaxLoad] = useState<number | null>(null)

  const doFetch = useCallback(async () => {
    const range = customRange ?? quickToRange(quickRange)
    setLoading(true)
    try {
      const result = await fetchLoadRange(
        range[0].toISOString(),
        range[1].toISOString(),
      )
      setData(result)
    } catch {
      message.error('数据加载失败 // 后端无响应')
    } finally {
      setLoading(false)
    }
  }, [quickRange, customRange])

  useEffect(() => {
    doFetch()
  }, [doFetch])

  const filtered = useMemo(() => {
    let rows = data
    if (minLoad != null) rows = rows.filter((d) => d.loadMw >= minLoad)
    if (maxLoad != null) rows = rows.filter((d) => d.loadMw <= maxLoad)
    return rows
  }, [data, minLoad, maxLoad])

  return (
    <div>
      {/* ====== 标题栏 ====== */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 12,
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <div>
          <h1 style={{ margin: 0, fontSize: 20, fontWeight: 900, letterSpacing: '-0.03em' }}>
            <span style={{ color: '#EAEAEA' }}>数据查询</span>
            <span style={{ color: '#666666', margin: '0 8px' }}>//</span>
            <span className="font-mono" style={{ color: '#888888', fontSize: 13, letterSpacing: '0.06em' }}>
              历史负荷记录
            </span>
          </h1>
          <span className="font-mono" style={{ color: '#666666', fontSize: 10, letterSpacing: '0.06em' }}>
            &lt; 按时间范围检索 · 支持负荷阈值过滤 · 分页浏览 &gt;
          </span>
        </div>

        <Space wrap>
          <Segmented
            value={quickRange}
            onChange={(v) => {
              setQuickRange(v as QuickRange)
              setCustomRange(null)
            }}
            options={[
              { label: '近24小时', value: '24h' },
              { label: '近7天', value: '7d' },
              { label: '近30天', value: '30d' },
            ]}
          />
          <RangePicker
            value={customRange}
            onChange={(dates) => {
              if (dates?.[0] && dates?.[1])
                setCustomRange([dates[0], dates[1]])
            }}
            showTime={quickRange === '24h' ? { format: 'HH:mm' } : undefined}
            format={quickRange === '24h' ? 'MM-DD HH:mm' : 'YYYY-MM-DD'}
            placeholder={['开始', '结束']}
            allowClear={!!customRange}
            style={{ width: quickRange === '24h' ? 340 : 280 }}
          />
        </Space>
      </div>

      <hr className="brutalist" />

      {/* ====== 过滤栏 ====== */}
      <div
        style={{
          margin: '12px 0',
          display: 'flex',
          gap: 12,
          flexWrap: 'wrap',
          alignItems: 'center',
        }}
      >
        <span className="font-mono" style={{ color: '#888888', fontSize: 11, letterSpacing: '0.06em' }}>
          筛选:
        </span>
        <span className="font-mono" style={{ color: '#666666', fontSize: 11 }}>
          负荷 &gt;=
        </span>
        <InputNumber
          placeholder="最小值"
          value={minLoad}
          onChange={(v) => setMinLoad(v)}
          style={{ width: 100 }}
          min={0}
        />
        <span className="font-mono" style={{ color: '#666666', fontSize: 11 }}>
          负荷 &lt;=
        </span>
        <InputNumber
          placeholder="最大值"
          value={maxLoad}
          onChange={(v) => setMaxLoad(v)}
          style={{ width: 100 }}
          min={0}
        />
        <Button
          icon={<ReloadOutlined />}
          onClick={doFetch}
          loading={loading}
          style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}
        >
          刷新
        </Button>
        <span
          className="font-mono"
          style={{ color: '#666666', fontSize: 11, marginLeft: 'auto' }}
        >
          共 {filtered.length} 条记录
        </span>
      </div>

      {/* ====== 表格 ====== */}
      <Table<LoadData>
        columns={COLUMNS}
        dataSource={filtered}
        rowKey="id"
        loading={loading}
        size="middle"
        pagination={{
          defaultPageSize: 20,
          pageSizeOptions: ['20', '50', '100'],
          showSizeChanger: true,
          showTotal: (total) => (
            <span className="font-mono" style={{ fontSize: 11, color: '#888888' }}>
              共 {total} 条
            </span>
          ),
        }}
        scroll={{ x: 800 }}
        locale={{
          emptyText: (
            <span className="font-mono" style={{ color: '#666666', fontSize: 12 }}>
              // 暂无数据 //
            </span>
          ),
        }}
        style={{ border: '1px solid #2A2A2A' }}
      />
    </div>
  )
}

export default DataQuery
