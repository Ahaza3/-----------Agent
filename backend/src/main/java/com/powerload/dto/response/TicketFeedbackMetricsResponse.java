package com.powerload.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TicketFeedbackMetricsResponse {
    private LocalDateTime start;
    private LocalDateTime end;
    private Long nodeId;
    private Long ruleId;
    private String ruleVersion;
    private String sourceType;
    private String alertClassification;
    private String rootCauseCode;
    private String effectiveness;

    private long totalClosedTickets;
    private long trueAlertCount;
    private long falsePositiveCount;
    private long duplicateCount;
    private long inconclusiveCount;
    private long feedbackMissingCount;
    private long effectiveCount;
    private long partialCount;
    private long ineffectiveCount;
    private BigDecimal totalImpactLoadMw = BigDecimal.ZERO;
    private long sampleCount;
    private BigDecimal alertAccuracyPercent;
}
