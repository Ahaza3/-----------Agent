package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预测结果表 — prediction_result
 */
@Data
@TableName("prediction_result")
public class PredictionResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预测的时间点 */
    private LocalDateTime predictTime;

    /** 预测负荷值(MW) */
    private Float predictedLoad;

    /** 置信区间下界 */
    private Float lowerBound;

    /** 置信区间上界 */
    private Float upperBound;

    /** 关联模型版本ID */
    private Long modelVersionId;

    /** 预测生成时间 */
    private LocalDateTime createdAt;
}
