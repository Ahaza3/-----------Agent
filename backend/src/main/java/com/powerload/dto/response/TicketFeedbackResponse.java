package com.powerload.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class TicketFeedbackResponse {
    private Long ticketId;
    private Long alertId;
    private String sourceType;
    private String feedbackStatus;
    private String alertClassification;
    private String rootCauseCode;
    private String rootCauseDetail;
    private List<String> actionsTaken;
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
    private Map<String, Object> alertEvidence;
}
