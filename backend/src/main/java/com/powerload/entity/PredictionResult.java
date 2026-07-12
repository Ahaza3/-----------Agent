package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@TableName("prediction_result")
public class PredictionResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预测的时间点 */
    private LocalDateTime predictTime;


    private Long modelVersionId;

    /** 预测生成时间 */
    private LocalDateTime createdAt;
}
