import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Alert,
  Card,
  Col,
  Descriptions,
  Empty,
  Modal,
  Row,
  Radio,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
  Button,
} from 'antd'
import {
  ApartmentOutlined,
  ReloadOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import type { EChartsOption } from 'echarts'
import ReactECharts from 'echarts-for-react'
import { fetchGridRisk, fetchGridTopology, simulateGridScenario } from '../../services/topologyApi'
import type {
  GridNodeType,
  GridRiskLevel,
  GridRiskSnapshot,
  GridScenarioResponse,
  GridTopologyResponse,
} from '../../types/topology'
import './index.css'

const { Text, Title } = Typography

const RISK_COLORS: Record<GridRiskLevel, string> = {
  RED: '#D85C5C',
  ORANGE: '#D7A447',
  YELLOW: '#C6A34A',
  NORMAL: '#5FA777',
  UNKNOWN: '#6F7D8A',
}

const RISK_LABELS: Record<GridRiskLevel, string> = {
  RED: '红色风险',
  ORANGE: '橙色风险',
  YELLOW: '黄色风险',
  NORMAL: '正常',
  UNKNOWN: '未知',
}

const NODE_LABELS: Record<GridNodeType, string> = {
  REGION: '区域',
  SUBSTATION: '变电站',
  FEEDER: '馈线',
}

function formatMw(value: number | null): string {
  return value == null ? '--' : `${value.toFixed(1)} MW`
}

const TOPOLOGY_NODE_WIDTH: Record<GridNodeType, number> = {
  REGION: 148,
  SUBSTATION: 132,
  FEEDER: 112,
}

function riskTag(level: GridRiskLevel) {
  return (
    <Tag color={RISK_COLORS[level]} className="topology-risk-tag">
      {RISK_LABELS[level]}
    </Tag>
  )
}

const columns: ColumnsType<GridRiskSnapshot> = [
  {
    title: '节点',
    key: 'node',
    fixed: 'left',
    width: 200,
    render: (_, row) => (
      <div>
        <div className="topology-node-name">{row.nodeName}</div>
        <Text type="secondary" className="font-mono topology-node-code">
          {row.nodeCode}
        </Text>
      </div>
    ),
  },
  {
    title: '类型',
    dataIndex: 'nodeType',
    key: 'nodeType',
    width: 90,
    render: (value: GridNodeType) => NODE_LABELS[value],
  },
  {
    title: '当前负荷',
    dataIndex: 'currentLoadMw',
    key: 'currentLoadMw',
    width: 120,
    render: (value: number | null) => <span className="font-mono">{formatMw(value)}</span>,
  },
  {
    title: '预测峰值',
    dataIndex: 'forecastPeakMw',
    key: 'forecastPeakMw',
    width: 120,
    render: (value: number | null) => <span className="font-mono">{formatMw(value)}</span>,
  },
  {
    title: '容量余量',
    dataIndex: 'headroomMw',
    key: 'headroomMw',
    width: 120,
    render: (value: number | null) => (
      <span className="font-mono" style={{ color: value != null && value < 0 ? RISK_COLORS.RED : undefined }}>
        {formatMw(value)}
      </span>
    ),
  },
  {
    title: '风险',
    dataIndex: 'riskLevel',
    key: 'riskLevel',
    width: 120,
    render: (value: GridRiskLevel) => riskTag(value),
  },
  {
    title: '告警归属',
    key: 'alertRoot',
    width: 150,
    render: (_, row) => row.alertDeduplicated
      ? <Text type="secondary">已合并至 {row.alertRootNodeCode}</Text>
      : row.alertRootNodeCode
        ? <Tag color="red">根告警</Tag>
        : <Text type="secondary">--</Text>,
  },
]

const Topology = () => {
  const [topology, setTopology] = useState<GridTopologyResponse | null>(null)
  const [risk, setRisk] = useState<GridRiskSnapshot[]>([])
  const [nodeType, setNodeType] = useState<GridNodeType | 'ALL'>('ALL')
  const [riskLevel, setRiskLevel] = useState<GridRiskLevel | 'ALL'>('ALL')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [lastRiskRefreshAt, setLastRiskRefreshAt] = useState<Date | null>(null)
  const [scenarioOpen, setScenarioOpen] = useState(false)
  const [scenarioType, setScenarioType] = useState<'NODE' | 'EDGE'>('NODE')
  const [scenarioTargetId, setScenarioTargetId] = useState<number>()
  const [scenarioLoading, setScenarioLoading] = useState(false)
  const [scenarioResult, setScenarioResult] = useState<GridScenarioResponse | null>(null)
  const graphCanvasRef = useRef<HTMLDivElement>(null)
  const [graphCanvasWidth, setGraphCanvasWidth] = useState(520)

  useEffect(() => {
    const element = graphCanvasRef.current
    if (!element) return

    const updateWidth = () => {
      setGraphCanvasWidth(Math.max(340, Math.round(element.getBoundingClientRect().width)))
    }
    updateWidth()

    const observer = new ResizeObserver(updateWidth)
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const [topologyResult, riskResult] = await Promise.all([
        fetchGridTopology(),
        fetchGridRisk(),
      ])
      setTopology(topologyResult)
      setRisk(riskResult)
      setLastRiskRefreshAt(new Date())
    } catch (err) {
      setError(err instanceof Error ? err.message : '拓扑数据加载失败')
    } finally {
      setLoading(false)
    }
  }

  const refreshRisk = async () => {
    try {
      const riskResult = await fetchGridRisk()
      setRisk(riskResult)
      setLastRiskRefreshAt(new Date())
    } catch {
      // 保留最近一次风险快照，避免短暂接口抖动清空页面。
    }
  }

  useEffect(() => {
    load()
    const timer = window.setInterval(() => {
      refreshRisk()
    }, 5_000)
    return () => window.clearInterval(timer)
  }, [])

  const scenarioOptions = useMemo(() => scenarioType === 'NODE'
    ? (topology?.nodes ?? []).map((node) => ({
      value: node.id,
      label: `${node.nodeName} (${node.nodeCode})`,
    }))
    : (topology?.edges ?? []).map((edge) => ({
      value: edge.id,
      label: `${edge.fromNodeCode ?? edge.fromNodeId} → ${edge.toNodeCode ?? edge.toNodeId}`,
    })), [scenarioType, topology])

  const runScenario = async () => {
    if (scenarioTargetId == null) return
    setScenarioLoading(true)
    try {
      const result = await simulateGridScenario(scenarioType === 'NODE'
        ? { targetType: 'NODE', nodeId: scenarioTargetId }
        : { targetType: 'EDGE', edgeId: scenarioTargetId })
      setScenarioResult(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : '故障场景推演失败')
    } finally {
      setScenarioLoading(false)
    }
  }

  const filteredRisk = useMemo(
    () => risk.filter((row) =>
      (nodeType === 'ALL' || row.nodeType === nodeType)
      && (riskLevel === 'ALL' || row.riskLevel === riskLevel),
    ),
    [nodeType, risk, riskLevel],
  )

  const riskSummary = useMemo(() => ({
    total: risk.length,
    attention: risk.filter((row) => ['RED', 'ORANGE', 'YELLOW'].includes(row.riskLevel)).length,
    red: risk.filter((row) => row.riskLevel === 'RED').length,
    peak: risk[0]?.forecastPeakMw ?? null,
  }), [risk])

  const graphOption = useMemo<EChartsOption>(() => {
    if (!topology) return {}
    const riskByCode = new Map(risk.map((row) => [row.nodeCode, row]))
    const categoryIndex: Record<GridNodeType, number> = {
      REGION: 0,
      SUBSTATION: 1,
      FEEDER: 2,
    }
    const width = Math.max(340, graphCanvasWidth)
    const positions: Record<string, { x: number; y: number }> = {
      'REGION-DEMO': { x: width * 0.5, y: 410 },
      'SUBSTATION-EAST': { x: width * 0.3, y: 245 },
      'SUBSTATION-WEST': { x: width * 0.7, y: 245 },
      'FEEDER-E-01': { x: width * 0.16, y: 82 },
      'FEEDER-E-02': { x: width * 0.39, y: 82 },
      'FEEDER-W-01': { x: width * 0.61, y: 82 },
      'FEEDER-W-02': { x: width * 0.84, y: 82 },
    }
    const graphNodes = topology.nodes.map((node) => {
      const nodeRisk = riskByCode.get(node.nodeCode)
      const position = positions[node.nodeCode] ?? { x: width * 0.5, y: 245 }
      const riskLevel = nodeRisk?.riskLevel ?? 'UNKNOWN'
      return {
        id: String(node.id),
        name: node.nodeName,
        nodeCode: node.nodeCode,
        nodeType: node.nodeType,
        riskLevel,
        currentLoadMw: nodeRisk?.currentLoadMw,
        forecastPeakMw: nodeRisk?.forecastPeakMw,
        category: categoryIndex[node.nodeType],
        x: position.x,
        y: position.y,
        fixed: true,
        symbol: 'roundRect',
        symbolSize: [TOPOLOGY_NODE_WIDTH[node.nodeType], node.nodeType === 'REGION' ? 52 : 46],
        itemStyle: {
          color: '#14222D',
          borderColor: RISK_COLORS[riskLevel],
          borderWidth: 2,
          shadowBlur: 12,
          shadowColor: `${RISK_COLORS[riskLevel]}55`,
        },
      }
    })
    const graphLinks = topology.edges.map((edge) => ({
      source: String(edge.fromNodeId),
      target: String(edge.toNodeId),
    }))

    return {
      animation: false,
      animationDuration: 0,
      tooltip: {
        trigger: 'item',
        formatter: (params: any) => {
          if (params.dataType === 'edge') return ''
          const data = params.data
          return [
            `<strong>${data.name}</strong>`,
            `<span>${data.nodeCode}</span>`,
            `当前负荷：${formatMw(data.currentLoadMw)}`,
            `预测峰值：${formatMw(data.forecastPeakMw)}`,
          ].join('<br/>')
        },
      },
      legend: {
        top: 8,
        right: 8,
        textStyle: { color: '#9CAAB7' },
        data: ['区域', '变电站', '馈线'],
        formatter: (value: string) => value,
      },
      series: [{
        type: 'graph',
        layout: 'none',
        roam: true,
        draggable: false,
        data: graphNodes,
        links: graphLinks,
        categories: [
          { name: '区域' },
          { name: '变电站' },
          { name: '馈线' },
        ],
        label: {
          show: true,
          position: 'inside',
          color: '#DDEAF5',
          fontSize: 11,
          lineHeight: 16,
          formatter: (params: any) => [
            `{name|${params.data.name}}`,
            `{load|${formatMw(params.data.currentLoadMw)}}`,
          ].join('\n'),
          rich: {
            name: {
              color: '#E7EDF3',
              fontSize: 11,
              fontWeight: 650,
              width: 132,
              align: 'center',
            },
            load: {
              color: '#8FA4B5',
              fontSize: 10,
              width: 132,
              align: 'center',
              fontFamily: 'JetBrains Mono, IBM Plex Mono, monospace',
            },
          },
        },
        lineStyle: {
          color: '#4B6477',
          width: 2,
          curveness: 0,
        },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 3 },
        },
      }],
    }
  }, [graphCanvasWidth, risk, topology])

  return (
    <div className="topology-page">
      <div className="topology-page__header">
        <div>
          <div className="topology-kicker">
            <ApartmentOutlined />
            GRID TOPOLOGY
          </div>
          <Title level={2} className="topology-title">拓扑风险</Title>
          <Text type="secondary">从区域到馈线查看负荷分布、预测峰值与容量余量</Text>
        </div>
        <Space wrap>
          {topology && (
            <Tag color="gold">{topology.source === 'DERIVED_SIMULATION' ? '派生模拟' : topology.source}</Tag>
          )}
          {topology && <Tag>{topology.topologyVersion}</Tag>}
          {topology && (
            <Tag color="green">
              风险实时刷新{lastRiskRefreshAt ? ` · ${lastRiskRefreshAt.toLocaleTimeString()}` : ''}
            </Tag>
          )}
          <Button icon={<WarningOutlined />} onClick={() => setScenarioOpen(true)}>故障推演</Button>
          <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>刷新</Button>
        </Space>
      </div>

      {error && <Alert type="error" showIcon message={error} className="topology-alert" />}
      {topology?.simulated && (
        <Alert
          type="warning"
          showIcon
          icon={<WarningOutlined />}
          message="当前节点负荷、预测和故障影响均为独立节点模拟结果，仅用于验证拓扑风险链路，不代表真实 SCADA 或潮流计算。"
          className="topology-alert"
        />
      )}

      <Row gutter={[12, 12]} className="topology-stats">
        <Col xs={12} md={6}><Card><Statistic title="拓扑节点" value={riskSummary.total} suffix="个" /></Card></Col>
        <Col xs={12} md={6}><Card><Statistic title="需关注节点" value={riskSummary.attention} suffix="个" valueStyle={{ color: '#D7A447' }} /></Card></Col>
        <Col xs={12} md={6}><Card><Statistic title="红色风险" value={riskSummary.red} suffix="个" valueStyle={{ color: '#D85C5C' }} /></Card></Col>
        <Col xs={12} md={6}><Card><Statistic title="最高预测峰值" value={riskSummary.peak ?? '--'} suffix={riskSummary.peak == null ? '' : 'MW'} precision={1} /></Card></Col>
      </Row>

      <Row gutter={[12, 12]} align="stretch">
        <Col xs={24} xl={11}>
          <Card
            title="拓扑关系"
            extra={<Text type="secondary">{topology?.nodes.length ?? 0} 节点 / {topology?.edges.length ?? 0} 连接</Text>}
            className="topology-panel topology-graph-panel"
          >
            {loading && !topology ? (
              <div className="topology-loading"><Spin /></div>
            ) : topology ? (
              <div ref={graphCanvasRef} className="topology-graph-canvas">
                <ReactECharts option={graphOption} style={{ height: 500 }} notMerge lazyUpdate />
              </div>
            ) : (
              <Empty description="暂无拓扑数据" />
            )}
          </Card>
        </Col>
        <Col xs={24} xl={13}>
          <Card
            title="节点风险排序"
            extra={(
              <Space wrap>
                <Select
                  value={nodeType}
                  onChange={setNodeType}
                  options={[
                    { value: 'ALL', label: '全部节点' },
                    ...Object.entries(NODE_LABELS).map(([value, label]) => ({ value, label })),
                  ]}
                  style={{ width: 112 }}
                />
                <Select
                  value={riskLevel}
                  onChange={setRiskLevel}
                  options={[
                    { value: 'ALL', label: '全部风险' },
                    ...Object.entries(RISK_LABELS)
                      .filter(([value]) => value !== 'UNKNOWN')
                      .map(([value, label]) => ({ value, label })),
                  ]}
                  style={{ width: 112 }}
                />
              </Space>
            )}
            className="topology-panel"
          >
            <Table<GridRiskSnapshot>
              rowKey="nodeCode"
              columns={columns}
              dataSource={filteredRisk}
              loading={loading}
              pagination={false}
              scroll={{ x: 760, y: 500 }}
              size="small"
            />
          </Card>
        </Col>
      </Row>

      <Modal
        title="节点 / 线路故障推演"
        open={scenarioOpen}
        onCancel={() => setScenarioOpen(false)}
        footer={null}
        width={860}
      >
        <Space direction="vertical" size={14} style={{ width: '100%' }}>
          <Space wrap>
            <Radio.Group
              value={scenarioType}
              onChange={(event) => {
                setScenarioType(event.target.value)
                setScenarioTargetId(undefined)
                setScenarioResult(null)
              }}
              optionType="button"
              buttonStyle="solid"
              options={[
                { label: '节点故障', value: 'NODE' },
                { label: '线路故障', value: 'EDGE' },
              ]}
            />
            <Select
              showSearch
              optionFilterProp="label"
              value={scenarioTargetId}
              onChange={setScenarioTargetId}
              options={scenarioOptions}
              placeholder="选择故障目标"
              style={{ minWidth: 300 }}
            />
            <Button
              type="primary"
              icon={<WarningOutlined />}
              onClick={runScenario}
              loading={scenarioLoading}
              disabled={scenarioTargetId == null}
            >
              开始推演
            </Button>
          </Space>

          {scenarioResult && (
            <>
              <Descriptions bordered size="small" column={3}>
                <Descriptions.Item label="目标">{scenarioResult.targetName}</Descriptions.Item>
                <Descriptions.Item label="影响等级">{riskTag(scenarioResult.severity)}</Descriptions.Item>
                <Descriptions.Item label="基准负荷">{formatMw(scenarioResult.baselineLoadMw)}</Descriptions.Item>
                <Descriptions.Item label="受影响负荷">{formatMw(scenarioResult.affectedLoadMw)}</Descriptions.Item>
                <Descriptions.Item label="可转供余量">{formatMw(scenarioResult.transferableHeadroomMw)}</Descriptions.Item>
                <Descriptions.Item label="预计未供负荷">{formatMw(scenarioResult.unservedLoadMw)}</Descriptions.Item>
              </Descriptions>
              <Alert
                type={scenarioResult.unservedLoadMw > 0 ? 'error' : 'warning'}
                showIcon
                className="topology-alert"
                message={scenarioResult.unservedLoadMw > 0
                  ? '当前模拟余量不足以覆盖受影响负荷'
                  : '当前模拟结果显示可由未受影响节点承担转供'}
                description="结果由层级拓扑和容量余量规则计算，大模型只负责解释和生成处置建议。"
              />
              <Table
                rowKey="nodeCode"
                size="small"
                pagination={false}
                dataSource={scenarioResult.affectedNodes}
                columns={[
                  { title: '节点', dataIndex: 'nodeName' },
                  { title: '编码', dataIndex: 'nodeCode' },
                  { title: '类型', dataIndex: 'nodeType' },
                  { title: '当前负荷', dataIndex: 'currentLoadMw', render: formatMw },
                  { title: '预测峰值', dataIndex: 'forecastPeakMw', render: formatMw },
                ]}
              />
            </>
          )}
        </Space>
      </Modal>
    </div>
  )
}

export default Topology
