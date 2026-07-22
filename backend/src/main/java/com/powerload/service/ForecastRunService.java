package com.powerload.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerload.entity.ForecastRun;

import java.time.LocalDateTime;

public interface ForecastRunService {
    ForecastRun createOrReuseCompleted(ForecastRun run);

    void updateMetadata(ForecastRun run);

    void markCompleted(Long id, int predictionCount, LocalDateTime completedAt);

    void markFailed(Long id, String failureReason);

    Page<ForecastRun> page(Long nodeId, String status, LocalDateTime start, LocalDateTime end,
                            int page, int size);
}
