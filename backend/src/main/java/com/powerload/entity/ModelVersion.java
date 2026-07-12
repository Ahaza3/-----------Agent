package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**

@Data
@TableName("model_version")
public class ModelVersion {

    @TableId(type = IdType.AUTO)
    private Long id;


    private Float mape;

    /** RMSE */
    private Float rmse;

    /** 模型文件路径 */
    private String filePath;


    private Integer isActive;

    /** 训练完成时间 */
    private LocalDateTime trainedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
