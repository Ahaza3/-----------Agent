package com.powerload.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ForecastRunResponse {
    private String runId;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime dataCutoff;
    private Integer forecastHorizonHours;
    private Long nodeId;
    private String modelVersion;
    private String artifactChecksum;
    private LocalDateTime weatherIssuedAt;
    private String weatherFallbackReason;
    private Integer predictionCount;
    private LocalDateTime completedAt;
    private String failureReason;
}
