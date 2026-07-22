package com.powerload.scheduler;

import com.powerload.mapper.RealtimeTelemetryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeTelemetryRetentionScheduler {

    private static final int BATCH_SIZE = 1000;
    private static final int MAX_BATCHES_PER_RUN = 100;

    private final RealtimeTelemetryMapper realtimeTelemetryMapper;

    @Scheduled(cron = "0 10 * * * *")
    public void removeExpiredTelemetry() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        try {
            for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
                if (realtimeTelemetryMapper.deleteExpiredBatch(cutoff, BATCH_SIZE) < BATCH_SIZE) return;
            }
            log.warn("Realtime telemetry retention reached its bounded batch limit");
        } catch (Exception e) {
            log.warn("Realtime telemetry retention degraded: {}", e.getClass().getSimpleName());
        }
    }
}
