package com.powerload.service;

import com.powerload.dto.response.FixedLeadReviewCandidate;
import com.powerload.entity.LoadData;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.mapper.PredictionResultMapper;
import com.powerload.service.impl.PredictionOperationsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictionOperationsServiceImplTest {
    @Mock private LoadDataMapper loadDataMapper;
    @Mock private PredictionResultMapper predictionResultMapper;
    @InjectMocks private PredictionOperationsServiceImpl service;

    @Test
    void fixedSampleCalculatesMaeRmseMapeWmapeAndP90UnderForecast() {
        LocalDateTime t = LocalDateTime.of(2026, 7, 20, 8, 0);
        // actual=[100,200,300], predicted=[90,210,240]: MAE=26.67, RMSE=35.59, MAPE=11.67%, WMAPE=13.33%.
        when(predictionResultMapper.selectFixedLeadReviewCandidates(eq(1L), eq(24), any(), any(), isNull()))
                .thenReturn(List.of(candidate(t, 90), candidate(t.plusHours(1), 210), candidate(t.plusHours(2), 240)));
        when(loadDataMapper.selectList(any())).thenReturn(List.of(load(t, 100), load(t.plusHours(1), 200), load(t.plusHours(2), 300)));

        Map<String, Object> result = service.review(1L, 24, t, t.plusHours(2), null);

        assertEquals(3, result.get("sampleCount")); assertEquals(26.67, result.get("mae"));
        assertEquals(35.59, result.get("rmse")); assertEquals(11.67, result.get("mape")); assertEquals(13.33, result.get("wmape"));
        assertEquals(300.0, result.get("highLoadThresholdMw")); assertEquals(1L, result.get("highLoadSampleCount"));
        assertEquals(1, result.get("underForecastCount")); assertEquals(100.0, result.get("highLoadUnderForecastRate"));
    }

    @Test
    void leadHoursAreRequestedIndependentlyAndNoSamplesAreNotZero() {
        LocalDateTime t = LocalDateTime.of(2026, 7, 20, 8, 0);
        when(predictionResultMapper.selectFixedLeadReviewCandidates(eq(1L), eq(1), any(), any(), isNull())).thenReturn(List.of());
        when(loadDataMapper.selectList(any())).thenReturn(List.of());
        Map<String, Object> result = service.review(1L, 1, t, t, null);
        assertEquals("NO_SAMPLES", result.get("calculationStatus")); assertNull(result.get("mae")); assertNull(result.get("wmape"));
        verify(predictionResultMapper).selectFixedLeadReviewCandidates(eq(1L), eq(1), eq(t), eq(t), isNull());
        assertThrows(IllegalArgumentException.class, () -> service.review(1L, 2, t, t, null));
    }

    @Test
    void zeroActualExcludesMapeAndEarliestPeakWins() {
        LocalDateTime t = LocalDateTime.of(2026, 7, 20, 8, 0);
        when(predictionResultMapper.selectFixedLeadReviewCandidates(eq(1L), eq(4), any(), any(), isNull()))
                .thenReturn(List.of(candidate(t, 0), candidate(t.plusHours(1), 100), candidate(t.plusHours(2), 100)));
        when(loadDataMapper.selectList(any())).thenReturn(List.of(load(t, 0), load(t.plusHours(1), 100), load(t.plusHours(2), 100)));
        Map<String, Object> result = service.review(1L, 4, t, t.plusHours(2), null);
        assertEquals(1, result.get("mapeExcludedZeroActualCount")); assertEquals(0.0, result.get("mape"));
        assertEquals(t.plusHours(1), result.get("actualPeakTime")); assertEquals(t.plusHours(1), result.get("predictedPeakTime"));
        assertEquals(0L, result.get("peakTimeErrorHours"));
    }

    private static FixedLeadReviewCandidate candidate(LocalDateTime time, float predicted) {
        FixedLeadReviewCandidate value = new FixedLeadReviewCandidate(); value.setPredictTime(time); value.setPredictedLoad(predicted); value.setModelVersion("v1"); return value;
    }
    private static LoadData load(LocalDateTime time, float actual) { LoadData value = new LoadData(); value.setTime(time); value.setLoadMw(actual); return value; }
}
