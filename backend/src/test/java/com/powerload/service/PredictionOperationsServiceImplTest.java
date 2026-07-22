package com.powerload.service;

import com.powerload.entity.LoadData;
import com.powerload.entity.PredictionResult;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredictionOperationsServiceImplTest {

    @Mock
    private LoadDataMapper loadDataMapper;

    @Mock
    private PredictionResultMapper predictionResultMapper;

    @InjectMocks
    private PredictionOperationsServiceImpl service;

    @Test
    void shouldReportMissingAndWeatherGaps() {
        LocalDateTime t10 = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        LocalDateTime t8 = t10.minusHours(2);
        LoadData latest = load(t10, 1000f, 25f, 60f, "METER");
        when(loadDataMapper.selectOne(any())).thenReturn(latest);
        when(loadDataMapper.selectList(any())).thenReturn(List.of(
                load(t8, 900f, null, 60f, "METER"),
                latest));

        Map<String, Object> result = service.quality(t8, t10);

        assertEquals("WARNING", result.get("status"));
        assertEquals(3L, result.get("expectedPoints"));
        assertEquals(2L, result.get("actualPoints"));
        assertEquals(1L, result.get("missingPoints"));
        assertEquals(120L, result.get("maxGapMinutes"));
        assertEquals(1L, result.get("weatherMissingPoints"));
    }

    @Test
    void shouldUseLatestPredictionForEachTargetTime() {
        LocalDateTime t8 = LocalDateTime.of(2026, 7, 20, 8, 0);
        LocalDateTime t9 = t8.plusHours(1);
        when(predictionResultMapper.selectList(any())).thenReturn(List.of(
                prediction(t8, 900f, 100, 1L),
                prediction(t8, 950f, 200, 1L),
                prediction(t9, 1000f, 200, 1L)));
        when(loadDataMapper.selectList(any())).thenReturn(List.of(
                load(t8, 1000f, 25f, 60f, "METER"),
                load(t9, 1000f, 25f, 60f, "METER")));

        Map<String, Object> result = service.review(t8, t9);

        assertEquals("INSUFFICIENT_DATA", result.get("status"));
        assertEquals(2, result.get("evaluatedPoints"));
        assertEquals(2, result.get("candidatePoints"));
        assertEquals(2.5, result.get("mape"));
        assertEquals(35.36, result.get("rmse"));
        List<Map<String, Object>> series = (List<Map<String, Object>>) result.get("series");
        assertEquals("LEGACY", series.get(0).get("traceabilityStatus"));
    }

    private LoadData load(LocalDateTime time, float load, Float temperature, Float humidity, String source) {
        LoadData row = new LoadData();
        row.setTime(time);
        row.setLoadMw(load);
        row.setTemperature(temperature);
        row.setHumidity(humidity);
        row.setDataSource(source);
        return row;
    }

    private PredictionResult prediction(LocalDateTime time, float load, int offsetSeconds, long modelVersionId) {
        PredictionResult result = new PredictionResult();
        result.setPredictTime(time);
        result.setPredictedLoad(load);
        result.setCreatedAt(LocalDateTime.of(2026, 7, 20, 7, 0).plusSeconds(offsetSeconds));
        result.setModelVersionId(modelVersionId);
        return result;
    }
}
