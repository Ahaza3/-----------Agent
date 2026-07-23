package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_runtime_state")
public class AlertRuntimeState {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String stateKey;
    private Long nodeId;
    private Long ruleId;
    private String alertType;
    private String lifecycleState;
    private String currentLevel;
    private Long activeAlertId;
    private Integer occurrenceNo;
    private LocalDateTime overLimitSince;
    private LocalDateTime lastObservedAt;
    private LocalDateTime lastReceivedAt;
    private LocalDateTime lastEvaluatedAt;
    private LocalDateTime lastTriggeredAt;
    private LocalDateTime cooldownUntil;
    private LocalDateTime recoveredAt;
    private Boolean maintenanceSuppressed;
    private LocalDateTime shelvedUntil;
    private String ruleVersion;
    private String lastSourceInstanceId;
    private Long lastSourceSequence;
    private Integer suppressedCount;
    private Integer optimisticVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
