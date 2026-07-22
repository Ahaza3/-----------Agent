package com.powerload.scheduler;

import com.powerload.mapper.RealtimeTelemetryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RealtimeTelemetryRetentionSchedulerTest {

    @Test
    void removesExpiredTelemetryInBoundedBatches() {
        RealtimeTelemetryMapper mapper = mock(RealtimeTelemetryMapper.class);
        when(mapper.deleteExpiredBatch(any(LocalDateTime.class), anyInt())).thenReturn(1000, 10);
        RealtimeTelemetryRetentionScheduler scheduler = new RealtimeTelemetryRetentionScheduler(mapper);

        scheduler.removeExpiredTelemetry();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper, times(2)).deleteExpiredBatch(cutoff.capture(), eq(1000));
        assertTrue(cutoff.getValue().isBefore(LocalDateTime.now().minusHours(23).plusMinutes(1)));
    }
}
