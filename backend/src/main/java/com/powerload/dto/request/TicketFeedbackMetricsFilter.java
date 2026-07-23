package com.powerload.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TicketFeedbackMetricsFilter {
    private LocalDateTime start;
    private LocalDateTime end;
    private Long nodeId;
    private Long ruleId;
    private String ruleVersion;
    private String sourceType;
    private String alertClassification;
    private String rootCauseCode;
    private String effectiveness;
}
