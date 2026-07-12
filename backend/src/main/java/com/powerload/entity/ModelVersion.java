package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型版本实体 — 对应 model_version 表
 *
 * <p>同时只有一个模型版本处于活跃状态 (is_active = 1)。</p>
 */
@Data
@TableName("model_version")
public class ModelVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模型名称 (LSTM / Prophet) */
    private String modelName;

    /** 版本号 (v1.0 / v2.0) */
    private String version;

    /** MAPE 精度 (%) */
    private Float mape;

    /** RMSE */
    private Float rmse;

    /** 模型文件路径 */
    private String filePath;

    /** 超参数 (JSON) */
    private String hyperparams;

    /** 是否活跃 (同时只有一个活跃) */
    private Integer isActive;

    /** 训练完成时间 */
    private LocalDateTime trainedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
