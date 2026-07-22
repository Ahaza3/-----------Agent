/**
 * 电网拓扑 API — 对应 /api/v1/topology/*
 */
import api from './api'
import type {
  GridRiskSnapshot,
  GridScenarioRequest,
  GridScenarioResponse,
  GridTopologyResponse,
} from '../types/topology'

export function fetchGridTopology(): Promise<GridTopologyResponse> {
  return api.get('/topology')
}

export function fetchGridRisk(): Promise<GridRiskSnapshot[]> {
  return api.get('/topology/risk')
}

export function simulateGridScenario(request: GridScenarioRequest): Promise<GridScenarioResponse> {
  return api.post('/topology/scenario', request)
}
