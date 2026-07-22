export type GridNodeType = 'REGION' | 'SUBSTATION' | 'FEEDER'
export type GridRiskLevel = 'RED' | 'ORANGE' | 'YELLOW' | 'NORMAL' | 'UNKNOWN'

export interface GridNodeView {
  id: number
  nodeCode: string
  nodeName: string
  nodeType: GridNodeType
  parentId: number | null
  parentCode: string | null
  responsibleUserId: number | null
  responsibleUserName: string | null
  allocationRatio: number
  ratedCapacityMw: number | null
  voltageLevel: string | null
  status: string
  topologyVersion: string
  sortOrder: number
}

export interface GridEdgeView {
  id: number
  fromNodeId: number
  toNodeId: number
  fromNodeCode: string | null
  toNodeCode: string | null
  edgeType: string
  capacityMw: number | null
  status: string
  topologyVersion: string
}

export interface GridTopologyResponse {
  topologyVersion: string
  simulated: boolean
  source: string
  nodes: GridNodeView[]
  edges: GridEdgeView[]
}

export interface GridRiskSnapshot {
  nodeId: number
  nodeCode: string
  nodeName: string
  nodeType: GridNodeType
  parentCode: string | null
  ratedCapacityMw: number | null
  currentLoadMw: number | null
  forecastPeakMw: number | null
  headroomMw: number | null
  riskLevel: GridRiskLevel
  riskBasis: 'FORECAST_PEAK' | 'CURRENT_LOAD'
  forecastAvailable: boolean
  forecastAdjusted: boolean
  simulated: boolean
  source: string
  riskReason: string | null
  alertRootNodeCode: string | null
  alertDeduplicated: boolean
}

export interface GridScenarioRequest {
  targetType: 'NODE' | 'EDGE'
  nodeId?: number
  edgeId?: number
}

export interface GridScenarioNodeImpact {
  nodeId: number
  nodeCode: string
  nodeName: string
  nodeType: GridNodeType
  currentLoadMw: number | null
  forecastPeakMw: number | null
  impactLoadMw: number | null
  impactType: string
}

export interface GridScenarioEdgeImpact {
  edgeId: number
  fromNodeCode: string | null
  toNodeCode: string | null
  edgeType: string
  capacityMw: number | null
  impactType: string
}

export interface GridScenarioResponse {
  targetType: 'NODE' | 'EDGE'
  targetId: number
  targetCode: string
  targetName: string
  severity: GridRiskLevel
  baselineLoadMw: number
  affectedLoadMw: number
  transferableHeadroomMw: number
  unservedLoadMw: number
  simulated: boolean
  source: string
  affectedNodes: GridScenarioNodeImpact[]
  affectedEdges: GridScenarioEdgeImpact[]
  assumptions: string[]
}
