/**
 * 数据查询 — 历史负荷数据检索
 * P0 · Sprint 1 (Day 5 交付)
 *
 * 功能：时间范围筛选 + 负荷阈值过滤 + 分页表格
 */
import { useState, useEffect, useCallback, useMemo } from 'react'
import { Table, DatePicker, InputNumber, Button, Space, message, Segmented } from 'antd'
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
    width: 180,
    render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm'),
    sorter: (a, b) => dayjs(a.time).unix() - dayjs(b.time).unix(),
    defaultSortOrder: 'descend',
  },
  {
    title: '负荷 (MW)',
    dataIndex: 'loadMw',
    key: 'loadMw',
    width: 140,
    render: (v: number) => (
      <span style={{ fontWeight: 600 }}>{v.toFixed(1)}</span>
    ),
    sorter: (a, b) => a.loadMw - b.loadMw,
  },
  {
    title: '温度 (°C)',
    dataIndex: 'temperature',
    key: 'temperature',
    width: 120,
    render: (v: number) => v.toFixed(1),
  },
  {
    title: '湿度 (%)',
    dataIndex: 'humidity',
    key: 'humidity',
    width: 100,
    render: (v: number) => v.toFixed(0),
  },
  {
    title: '节假日',
    dataIndex: 'isHoliday',
    key: 'isHoliday',
    width: 80,
    render: (v: 0 | 1) => (v === 1 ? '是' : '否'),
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
    width: 70,
  },
  {
    title: '星期',
    dataIndex: 'dayOfWeek',
    key: 'dayOfWeek',
    width: 70,
    render: (v: number) => ['一', '二', '三', '四', '五', '六', '日'][v] ?? v,
  },
]

const DataQuery = () => {
  const [quickRange, setQuickRange] = useState<QuickRange>('24h')
  const [customRange, setCustomRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [data, setData] = useState<LoadData[]>([])
  const [loading, setLoading] = useState(false)
  const [minLoad, setMinLoad] = useState<number | null>(null)
  const [maxLoad, setMaxLoad] = useState<number | null>(null)

  const range = customRange ?? quickToRange(quickRange)

  // ---- 获取数据 ----
  const doFetch = useCallback(async () => {
    setLoading(true)
    try {
      const result = await fetchLoadRange(range[0].toISOString(), range[1].toISOString())
      setData(result)
    } catch {
      message.error('数据加载失败，请确认后端服务已启动')
    } finally {
      setLoading(false)
    }
  }, [range])

  useEffect(() => {
    doFetch()
  }, [doFetch])

  // ---- 筛选后的数据 ----
  const filtered = useMemo(() => {
    let rows = data
    if (minLoad != null) rows = rows.filter((d) => d.loadMw >= minLoad)
    if (maxLoad != null) rows = rows.filter((d) => d.loadMw <= maxLoad)
    return rows
  }, [data, minLoad, maxLoad])

  return (
    <div>
      {/* ====== 筛选栏 ====== */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <h1 style={{ margin: 0, color: '#e0e6f0', fontSize: 20, fontWeight: 600 }}>
          📊 数据查询
        </h1>
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
              if (dates?.[0] && dates?.[1]) setCustomRange([dates[0], dates[1]])
            }}
            showTime={quickRange === '24h' ? { format: 'HH:mm' } : undefined}
            format={quickRange === '24h' ? 'MM-DD HH:mm' : 'YYYY-MM-DD'}
            allowClear={!!customRange}
            style={{ width: quickRange === '24h' ? 340 : 280 }}
          />
        </Space>
      </div>

      {/* ====== 阈值过滤 + 刷新 ====== */}
      <div style={{ marginBottom: 16, display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
        <span style={{ color: '#8892a4', fontSize: 13 }}>负荷范围:</span>
        <InputNumber
          placeholder="最小值"
          value={minLoad}
          onChange={(v) => setMinLoad(v)}
          style={{ width: 120 }}
          min={0}
        />
        <span style={{ color: '#5c6680' }}>—</span>
        <InputNumber
          placeholder="最大值"
          value={maxLoad}
          onChange={(v) => setMaxLoad(v)}
          style={{ width: 120 }}
          min={0}
        />
        <Button
          icon={<ReloadOutlined />}
          onClick={doFetch}
          loading={loading}
        >
          刷新
        </Button>
        <span style={{ color: '#5c6680', fontSize: 12, marginLeft: 'auto' }}>
          共 {filtered.length} 条
        </span>
      </div>

      {/* ====== 数据表格 ====== */}
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
          showTotal: (total) => `共 ${total} 条`,
        }}
        scroll={{ x: 900 }}
        locale={{ emptyText: '所选时间范围内无数据' }}
      />
    </div>
  )
}

export default DataQuery
