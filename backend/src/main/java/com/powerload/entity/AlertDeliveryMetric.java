package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_delivery_metric")
public class AlertDeliveryMetric {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long alertId;
    private Long userId;
    private String username;
    private String role;
    private String clientSessionId;
    private LocalDateTime clientRenderedAt;
    private LocalDateTime ackReceivedAt;
    private Long latencyMs;
    private String invalidReason;
    private LocalDateTime createdAt;
}
