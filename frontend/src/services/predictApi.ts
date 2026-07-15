/**
 * 预测 API — 对应 /api/v1/predict/*
 */
import api from './api'
import type { ForecastResponse } from '../types/prediction'

/** GET /api/v1/predict/forecast — 获取 24 小时负荷预测 */
export function fetchForecast(): Promise<ForecastResponse> {
  return api.get('/predict/forecast')
}
