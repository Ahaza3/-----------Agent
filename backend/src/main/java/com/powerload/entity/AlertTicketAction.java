package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("alert_ticket_action")
public class AlertTicketAction {
    @TableId(type = IdType.AUTO) private Long id;
    private Long ticketId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private String note;
    private LocalDateTime createdAt;
}
