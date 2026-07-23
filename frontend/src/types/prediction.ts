/**
 * 预测接口响应
 * 对应后端 ForecastResponse DTO — GET /api/v1/predict/forecast
 */
export interface ForecastResponse {
  nodeId?: number | null
  source?: string | null
  /** 24 小时预测值 (MW)，从 forecastStartTime 起每小时一个 */
  predictions: number[]
  /** 模型名称: LSTM / Prophet */
  model: string
  /** 预测基准时间 — 第一个预测值对应的时间（ISO 8601，Asia/Shanghai） */
  forecastStartTime: string | null
  lowerBounds?: number[] | null
  upperBounds?: number[] | null
  intervalSource?: string | null
  modelVersionId?: number | null
  futureWeatherAvailable?: boolean
  weatherSource?: string | null
  futureWeatherApplied?: boolean
  futureWeatherFallback?: boolean
  runId?: string | null
  issuedAt?: string | null
  dataCutoff?: string | null
  forecastHorizonHours?: number | null
  modelVersion?: string | null
  artifactChecksum?: string | null
  weatherIssuedAt?: string | null
  traceabilityStatus?: 'TRACEABLE' | 'UNTRACEABLE' | string | null
}

/**
 * 预测结果类型
 * 对应后端 prediction_result 表
 */
export interface PredictionResult {
  /** 主键 */
  id: number
  /** 预测目标时间点 (ISO 8601) */
  predictTime: string
  /** 预测负荷值 (MW) */
  predictValue: number
  /** 置信区间下界 (95% CI) */
  lowerBound?: number
  /** 置信区间上界 (95% CI) */
  upperBound?: number
  /** 模型名称: prophet / lstm */
  modelName: string
  /** 模型版本号 */
  modelVersion: string
  /** 对应的实际值（预测后回填，可能为空） */
  actualValue?: number
  /** 预测误差 MAPE (%)，事后计算 */
  mape?: number
  /** 创建时间 */
  createTime: string
}

/**
 * 模型版本信息
 * 对应后端 model_version 表
 */
export interface ModelVersion {
  id: number
  modelName: 'LSTM' | 'Prophet' | string
  version: string
  mape?: number | null
  rmse?: number | null
  filePath?: string | null
  artifactDir?: string | null
  artifactChecksum?: string | null
  runtimeStatus?: string | null
  deployedAt?: string | null
  lastLoadError?: string | null
  lastHealthCheckedAt?: string | null
  hyperparams?: string | null
  isActive: number
  trainedAt?: string | null
  createdAt?: string | null
}
