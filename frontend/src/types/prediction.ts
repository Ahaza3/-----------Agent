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
  /** 主键 */
  id: number
  /** 模型名称 */
  modelName: string
  /** 版本号 */
  version: string
  /** 模型文件路径 */
  filePath: string
  /** 训练数据集描述 */
  trainDataset: string
  /** 训练 MAPE */
  trainMape: number
  /** 是否当前激活版本 */
  active: boolean
  /** 部署时间 */
  deployTime: string
  /** 创建时间 */
  createTime: string
}
