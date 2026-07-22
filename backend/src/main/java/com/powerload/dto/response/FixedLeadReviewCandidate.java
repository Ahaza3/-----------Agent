package com.powerload.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FixedLeadReviewCandidate {
    private Long predictionId;
    private LocalDateTime predictTime;
    private Float predictedLoad;
    private Long modelVersionId;
    private String modelVersion;
    private LocalDateTime issuedAt;
}
