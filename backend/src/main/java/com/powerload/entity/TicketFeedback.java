package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ticket_feedback")
public class TicketFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ticketId;
    private Long alertId;
    private String sourceType;
    private String alertClassification;
    private String rootCauseCode;
    private String rootCauseDetail;
    /** MySQL JSON 数组，服务层负责结构化校验和序列化。 */
    private String actionsTaken;
    private String actionDetail;
    private BigDecimal impactLoadMw;
    private String effectiveness;
    private Long operatorUserId;
    private String operatorName;
    private Long reviewerUserId;
    private String reviewerName;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
