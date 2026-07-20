/**
 * 数据查询 API — 对应 /api/v1/data/*
 */
import api from './api'
import type { LoadData, LoadStats, RealtimeLoadPoint } from '../types/load'

/** GET /api/v1/data/range — 按时间范围查询负荷数据 */
export function fetchLoadRange(start: string, end: string): Promise<LoadData[]> {
  return api.get('/data/range', { params: { start, end } })
}

/** GET /api/v1/data/latest — 获取最新一条负荷记录 */
export function fetchLoadLatest(): Promise<LoadData> {
  return api.get('/data/latest')
}

/** GET /api/v1/data/stats — 获取时间段统计概览 */
export function fetchLoadStats(start: string, end: string): Promise<LoadStats> {
  return api.get('/data/stats', { params: { start, end } })
}

/** GET /api/v1/data/realtime/recent — 获取最近 N 分钟实时数据快照 */
export function fetchRealtimeRecent(minutes = 30): Promise<RealtimeLoadPoint[]> {
  return api.get('/data/realtime/recent', { params: { minutes } })
}
