package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_advice")
public class AlertAdvice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long alertId;
    private String audienceRole;
    private String status; // PENDING/SUCCESS/FAILED/FALLBACK
    private String analysis;
    private String actions;
    private String evidence;
    private String modelName;
    private String errorMessage;
    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
