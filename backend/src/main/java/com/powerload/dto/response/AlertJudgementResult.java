package com.powerload.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能研判结果 — 规则型 Agent 输出
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertJudgementResult {

    private Long alertId;
    private String level;
    private Float currentLoad;
    private Float thresholdValue;
    private String trendDirection;   // RISING / STABLE / FALLING / UNKNOWN
    private Float forecastPeakLoad;
    private LocalDateTime forecastPeakTime;
    private Boolean hasExistingTicket;
    private Boolean hasOpenSimilarTicket;
    private Boolean shouldCreateTicket;
    private Boolean autoCreateTicket;
    private String recommendedPriority; // URGENT / HIGH / NORMAL
    private String dispatcherAdvice;
    private String operatorAdvice;
    private String decisionReason;
    private String source;           // RULE_BASED_AGENT
    private LocalDateTime createdAt;
}
