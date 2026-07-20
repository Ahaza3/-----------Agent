package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("model_training_task")
public class ModelTrainingTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String modelName;
    private String status;
    private LocalDateTime dataStart;
    private LocalDateTime dataEnd;
    private Integer sampleCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String message;
    private String outputTail;
    private String artifactManifest;
    private LocalDateTime createdAt;
}
