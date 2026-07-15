/**
 * 告警 API — GET /api/v1/alert/*
 */
import api from './api'
import type { AlertEvent, AlertPageResult } from '../types/alert'

interface QueryParams {
  level?: string
  start?: string
  end?: string
  page?: number
  size?: number
  unreadOnly?: boolean
}

export function fetchAlertEvents(params: QueryParams = {}): Promise<AlertPageResult> {
  return api.get('/alert/events', { params })
}

export function markAlertRead(id: number): Promise<void> {
  return api.put(`/alert/events/${id}/read`)
}

export function fetchLatestAlerts(): Promise<AlertEvent[]> {
  return fetchAlertEvents({ page: 1, size: 5, unreadOnly: true }).then((r) => r.records)
}
