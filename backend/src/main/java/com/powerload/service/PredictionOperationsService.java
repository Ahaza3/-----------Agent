package com.powerload.service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Prediction operations used by the operations console.
 */
public interface PredictionOperationsService {

    Map<String, Object> quality(LocalDateTime start, LocalDateTime end);

    Map<String, Object> review(LocalDateTime start, LocalDateTime end);

    Map<String, Object> review(Long nodeId, int leadHour, LocalDateTime start, LocalDateTime end,
                               String modelVersion);
}
