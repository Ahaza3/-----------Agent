package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("alert_ticket")
public class AlertTicket {
    @TableId(type = IdType.AUTO) private Long id;
    private String ticketNo;
    private Long alertId;
    private String sourceType; // ALERT/PREWARNING
    private String riskLevel;  // RED/ORANGE/YELLOW
    private LocalDateTime forecastTime;
    private Float expectedLoad;
    private String priority; // URGENT/HIGH/NORMAL
    private String status;   // PENDING/ASSIGNED/IN_PROGRESS/RESOLVED/CLOSED/CANCELLED
    private String summary;
    private Long assigneeUserId;
    private String assigneeName;
    private Long createdBy;
    private String createdByName;
    private String resolution;
    private LocalDateTime createdAt;
    private LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime updatedAt;
    private Integer version;
}
