package com.powerload.service.impl;

import com.powerload.entity.LoadData;
import com.powerload.entity.PredictionResult;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.mapper.PredictionResultMapper;
import com.powerload.ml.FlaskInferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PredictServiceImplTest {

    private LoadDataMapper loadDataMapper;
    private PredictionResultMapper predictionResultMapper;
    private FlaskInferenceService flaskInferenceService;
    private PredictServiceImpl predictService;

    @BeforeEach
    void setUp() {
        loadDataMapper = mock(LoadDataMapper.class);
        predictionResultMapper = mock(PredictionResultMapper.class);
        flaskInferenceService = mock(FlaskInferenceService.class);
        predictService = new PredictServiceImpl(loadDataMapper, predictionResultMapper, flaskInferenceService);
    }

    @Test
    void shouldReturnErrorWhenInsufficientHourlyData() {
        List<LoadData> few = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            few.add(hourlyRecord(LocalDateTime.of(2026, 7, 10, 0, 0).plusHours(i), 800f + i));
        }
        when(loadDataMapper.selectList(any())).thenReturn(few);

        var ex = assertThrows(IllegalStateException.class, () -> predictService.forecast());
        assertTrue(ex.getMessage().contains("数据不足"));
        assertTrue(ex.getMessage().contains("50"));
    }

    @Test
    void shouldIgnoreNonHourlyRecordsInInput() {
        List<LoadData> mixed = new ArrayList<>();
        for (int i = 0; i < 170; i++) {
            mixed.add(hourlyRecord(LocalDateTime.of(2026, 7, 10, 0, 0).plusHours(i), 800f + i));
        }
        // 非整点数据不会被查出来（WHERE MINUTE=0 AND SECOND=0）

        when(loadDataMapper.selectList(any())).thenReturn(mixed.subList(0, 170));

        var predictions = genPredictions();
        when(flaskInferenceService.forecast(any())).thenReturn(predictions);
        when(predictionResultMapper.insert(any(PredictionResult.class))).thenReturn(1);

        var result = predictService.forecast();
        assertNotNull(result);
        assertEquals(24, result.getPredictions().size());
        assertEquals("LSTM", result.getModel());
        assertNotNull(result.getForecastStartTime());
        verify(predictionResultMapper, times(24)).insert(any(PredictionResult.class));
    }

    @Test
    void shouldPersist24RecordsWithSameBatchTime() {
        List<LoadData> raw = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            raw.add(hourlyRecord(LocalDateTime.of(2026, 7, 8, 0, 0).plusHours(i), 800f + (i % 24)));
        }
        when(loadDataMapper.selectList(any())).thenReturn(raw);
        when(flaskInferenceService.forecast(any())).thenReturn(genPredictions());

        var captured = new ArrayList<PredictionResult>();
        doAnswer(inv -> {
            captured.add(inv.getArgument(0, PredictionResult.class));
            return 1;
        }).when(predictionResultMapper).insert(any(PredictionResult.class));

        predictService.forecast();

        assertEquals(24, captured.size());
        LocalDateTime firstCreatedAt = captured.get(0).getCreatedAt();
        for (PredictionResult pr : captured) {
            assertEquals(firstCreatedAt, pr.getCreatedAt());
            assertNotNull(pr.getPredictTime());
            assertNotNull(pr.getPredictedLoad());
        }
        // predictTime 递增一小时
        for (int i = 1; i < captured.size(); i++) {
            assertEquals(
                    captured.get(i - 1).getPredictTime().plusHours(1),
                    captured.get(i).getPredictTime());
        }
    }

    private static List<Double> genPredictions() {
        var list = new ArrayList<Double>();
        for (int i = 0; i < 24; i++) list.add(900.0 + i * 5.0);
        return list;
    }

    private static LoadData hourlyRecord(LocalDateTime time, float loadMw) {
        LoadData d = new LoadData();
        d.setTime(time);
        d.setLoadMw(loadMw);
        d.setTemperature(25f);
        d.setHumidity(60f);
        d.setHour(time.getHour());
        d.setDayOfWeek(time.getDayOfWeek().getValue() % 7);
        d.setMonth(time.getMonthValue());
        d.setIsHoliday(0);
        return d;
    }
}
