package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("forecast_run")
public class ForecastRun {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime dataCutoff;
    private Integer forecastHorizonHours;
    private Long nodeId;
    private Long modelVersionId;
    private String modelVersion;
    private String artifactChecksum;
    private LocalDateTime weatherIssuedAt;
    private String weatherFallbackReason;
    private Integer predictionCount;
    private String failureReason;
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
