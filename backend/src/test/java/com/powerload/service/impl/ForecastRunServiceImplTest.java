package com.powerload.service.impl;

import com.powerload.entity.ForecastRun;
import com.powerload.mapper.ForecastRunMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ForecastRunServiceImplTest {

    @Test
    void shouldReuseOnlyCompletedRunForIdempotencyKey() {
        ForecastRunMapper mapper = mock(ForecastRunMapper.class);
        ForecastRunServiceImpl service = new ForecastRunServiceImpl(mapper);
        ForecastRun completed = new ForecastRun();
        completed.setStatus("COMPLETED");
        completed.setRunId("FR-existing");
        when(mapper.selectOne(any())).thenReturn(completed);
        ForecastRun requested = new ForecastRun();
        requested.setIdempotencyKey("retry-key");

        ForecastRun actual = service.createOrReuseCompleted(requested);

        assertSame(completed, actual);
        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.<ForecastRun>any());
    }

    @Test
    void shouldCreateNewRunWithoutReusableCompletedRun() {
        ForecastRunMapper mapper = mock(ForecastRunMapper.class);
        ForecastRunServiceImpl service = new ForecastRunServiceImpl(mapper);
        when(mapper.selectOne(any())).thenReturn(null);
        ForecastRun requested = new ForecastRun();
        requested.setRunId("FR-new");
        requested.setIdempotencyKey("retry-key");

        ForecastRun actual = service.createOrReuseCompleted(requested);

        assertSame(requested, actual);
        verify(mapper).insert(org.mockito.ArgumentMatchers.<ForecastRun>any());
    }
}
