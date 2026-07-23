package com.powerload.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AlertDeliveryMetricsResponse {
    private long deliverySamples;
    private Long p50LatencyMs;
    private Long p95LatencyMs;
    private Long maxLatencyMs;
    private long excludedLegacyCount;
    private long excludedInvalidCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long nodeId;
}
