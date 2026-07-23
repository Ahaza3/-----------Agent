package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("realtime_telemetry")
public class RealtimeTelemetry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long nodeId;
    private LocalDateTime observedAt;
    private LocalDateTime receivedAt;
    private String sourceInstanceId;
    private Long sequence;
    private BigDecimal loadMw;
    private BigDecimal temperature;
    private BigDecimal humidity;
    private String qualityCode;
    private String dataSource;
    private Boolean estimated;
    private String persistenceStatus;
    private String qualityReason;
    private LocalDateTime createdAt;
}
