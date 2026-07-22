package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预测结果实体 — 对应 prediction_result 表
 *
 * <p>每次模型推理生成 24h 预测，取 created_at 最大的一批即为最新预测。</p>
 */
@Data
@TableName("prediction_result")
public class PredictionResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预测目标拓扑节点 ID；历史单区域预测归属区域根节点 */
    private Long nodeId;

    /** 预测的时间点 */
    private LocalDateTime predictTime;

    /** 预测负荷值 (MW) */
    private Float predictedLoad;

    /** 置信区间下界 (80%) */
    private Float lowerBound;

    /** 置信区间上界 (80%) */
    private Float upperBound;

    /** 关联模型版本 ID */
    private Long modelVersionId;

    /** 预测生成时间 */
    private LocalDateTime createdAt;
}
