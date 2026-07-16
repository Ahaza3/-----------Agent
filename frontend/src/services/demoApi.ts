import api from './api'

export type DemoLoadMode = 'NORMAL' | 'SPIKE' | 'RECOVERY'

export interface DemoLoadStatus {
  mode: DemoLoadMode
  currentLoad: number
  targetLoad: number
  normalTargetLoad: number
  safetyThreshold: number
  yellowThreshold: number
  orangeThreshold: number
  redThreshold: number
}

export function fetchDemoLoadStatus(): Promise<DemoLoadStatus> {
  return api.get('/demo/load/status')
}

export function setDemoLoadMode(
  command: 'normal' | 'spike' | 'recover',
): Promise<DemoLoadStatus> {
  return api.post(`/demo/load/${command}`)
}
