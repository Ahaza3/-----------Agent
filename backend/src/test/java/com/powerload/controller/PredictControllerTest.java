package com.powerload.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerload.dto.response.ForecastResponse;
import com.powerload.entity.ForecastRun;
import com.powerload.security.SysUserPrincipal;
import com.powerload.service.ForecastRunService;
import com.powerload.service.PredictService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PredictControllerTest {

    @Test
    void dispatcherCanOnlySeeCompletedSummaryAndNoFailureReason() {
        PredictService predictService = mock(PredictService.class);
        ForecastRunService runService = mock(ForecastRunService.class);
        PredictController controller = new PredictController(predictService, runService);
        ForecastRun run = failedRun();
        when(runService.page(eq(1L), eq("COMPLETED"), any(), any(), eq(1), eq(20)))
                .thenReturn(pageOf(run));

        var response = controller.runs(1L, "FAILED", null, null, 1, 20,
                new SysUserPrincipal(1L, "dispatcher", "DISPATCHER"));

        assertEquals("FAILED", response.getData().getRecords().get(0).getStatus());
        assertNull(response.getData().getRecords().get(0).getFailureReason());
        verify(runService).page(1L, "COMPLETED", null, null, 1, 20);
    }

    @Test
    void operatorCanSeeFailureReason() {
        PredictService predictService = mock(PredictService.class);
        ForecastRunService runService = mock(ForecastRunService.class);
        PredictController controller = new PredictController(predictService, runService);
        ForecastRun run = failedRun();
        when(runService.page(eq(1L), eq("FAILED"), any(), any(), eq(1), eq(20)))
                .thenReturn(pageOf(run));

        var response = controller.runs(1L, "FAILED", null, null, 1, 20,
                new SysUserPrincipal(2L, "operator", "OPERATOR"));

        assertEquals("DATABASE_WRITE_FAILED", response.getData().getRecords().get(0).getFailureReason());
    }

    @Test
    void forecastKeepsOldResponseAndPassesIdempotencyKey() {
        PredictService predictService = mock(PredictService.class);
        ForecastRunService runService = mock(ForecastRunService.class);
        PredictController controller = new PredictController(predictService, runService);
        ForecastResponse forecast = new ForecastResponse();
        forecast.setPredictions(List.of(900.0));
        forecast.setRunId("FR-1");
        forecast.setTraceabilityStatus("TRACEABLE");
        when(predictService.forecast(1L, "key-1")).thenReturn(forecast);

        var response = controller.forecast(1L, "key-1");

        assertEquals(List.of(900.0), response.getData().getPredictions());
        assertEquals("FR-1", response.getData().getRunId());
    }

    private static ForecastRun failedRun() {
        ForecastRun run = new ForecastRun();
        run.setRunId("FR-failed");
        run.setStatus("FAILED");
        run.setIssuedAt(LocalDateTime.of(2026, 7, 23, 10, 0));
        run.setFailureReason("DATABASE_WRITE_FAILED");
        return run;
    }

    private static Page<ForecastRun> pageOf(ForecastRun run) {
        Page<ForecastRun> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(run));
        return page;
    }
}
