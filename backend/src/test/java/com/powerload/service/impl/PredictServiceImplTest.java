package com.powerload.service.impl;

import com.powerload.entity.ForecastRun;
import com.powerload.entity.LoadData;
import com.powerload.entity.ModelVersion;
import com.powerload.entity.PredictionResult;
import com.powerload.entity.WeatherForecast;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.mapper.ModelVersionMapper;
import com.powerload.mapper.PredictionResultMapper;
import com.powerload.ml.FlaskInferenceService;
import com.powerload.service.ForecastRunService;
import com.powerload.service.WeatherForecastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PredictServiceImplTest {

    private LoadDataMapper loadDataMapper;
    private PredictionResultMapper predictionResultMapper;
    private ModelVersionMapper modelVersionMapper;
    private FlaskInferenceService flaskInferenceService;
    private ForecastRunService forecastRunService;
    private PredictServiceImpl predictService;

    @BeforeEach
    void setUp() {
        loadDataMapper = mock(LoadDataMapper.class);
        predictionResultMapper = mock(PredictionResultMapper.class);
        modelVersionMapper = mock(ModelVersionMapper.class);
        flaskInferenceService = mock(FlaskInferenceService.class);
        forecastRunService = mock(ForecastRunService.class);
        when(modelVersionMapper.selectList(any())).thenReturn(List.of());
        when(forecastRunService.createOrReuseCompleted(any())).thenAnswer(invocation -> {
            ForecastRun run = invocation.getArgument(0);
            run.setId(101L);
            return run;
        });
        when(predictionResultMapper.insert(any(PredictionResult.class))).thenReturn(1);
        predictService = new PredictServiceImpl(loadDataMapper, predictionResultMapper,
                flaskInferenceService, modelVersionMapper, forecastRunService);
    }

    @Test
    void shouldReturnErrorWhenInsufficientHourlyData() {
        when(loadDataMapper.selectList(any())).thenReturn(hourlyRows(50));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> predictService.forecast());
        assertTrue(exception.getMessage().contains("数据不足"));
        verifyNoInteractions(forecastRunService);
    }

    @Test
    void shouldCreateOneTraceableRunAndLeadHoursOneToTwentyFour() {
        when(loadDataMapper.selectList(any())).thenReturn(hourlyRows(200));
        when(flaskInferenceService.forecast(any())).thenReturn(forecastResult());
        ModelVersion version = new ModelVersion();
        version.setId(7L);
        version.setModelName("LSTM");
        version.setVersion("v7");
        when(modelVersionMapper.selectList(any())).thenReturn(List.of(version));
        List<PredictionResult> captured = new ArrayList<>();
        doAnswer(invocation -> {
            captured.add(invocation.getArgument(0));
            return 1;
        }).when(predictionResultMapper).insert(any(PredictionResult.class));

        var response = predictService.forecast(4L, "replay-001");

        assertEquals(24, captured.size());
        assertNotNull(response.getRunId());
        assertEquals("TRACEABLE", response.getTraceabilityStatus());
        assertEquals(24, response.getForecastHorizonHours());
        assertEquals(7L, captured.get(0).getModelVersionId());
        for (int i = 0; i < captured.size(); i++) {
            assertEquals(101L, captured.get(i).getForecastRunId());
            assertEquals(i + 1, captured.get(i).getLeadHours());
        }
        verify(forecastRunService).markCompleted(eq(101L), eq(24), any());
    }

    @Test
    void shouldMarkRunFailedWithoutCompletingPartialBatch() {
        when(loadDataMapper.selectList(any())).thenReturn(hourlyRows(200));
        when(flaskInferenceService.forecast(any())).thenReturn(forecastResult());
        when(predictionResultMapper.insert(any(PredictionResult.class))).thenReturn(1, 0);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> predictService.forecast());

        assertEquals("PREDICTION_RESULT_PERSIST_FAILED", exception.getMessage());
        verify(forecastRunService).markFailed(eq(101L), eq("PREDICTION_RESULT_PERSIST_FAILED"));
        verify(forecastRunService, never()).markCompleted(anyLong(), anyInt(), any());
    }

    @Test
    void shouldCaptureDataCutoffChecksumAndWeatherBatch(@TempDir Path tempDir) throws Exception {
        when(loadDataMapper.selectList(any())).thenReturn(hourlyRows(200));
        when(flaskInferenceService.forecast(anyList(), anyList())).thenReturn(forecastResult());
        Path artifact = tempDir.resolve("lstm.pt");
        Files.writeString(artifact, "known-artifact");
        ModelVersion version = new ModelVersion();
        version.setModelName("LSTM");
        version.setVersion("v1.2");
        version.setFilePath(artifact.toString());
        when(modelVersionMapper.selectList(any())).thenReturn(List.of(version));
        WeatherForecastService weatherService = mock(WeatherForecastService.class);
        LocalDateTime issuedAt = LocalDateTime.of(2026, 7, 23, 8, 0);
        List<WeatherForecast> weatherRows = new ArrayList<>();
        LocalDateTime start = LocalDateTime.now().withSecond(0).withNano(0).withMinute(0).plusHours(1);
        for (int i = 0; i < 24; i++) {
            WeatherForecast point = new WeatherForecast();
            point.setForecastTime(start.plusHours(i));
            point.setIssuedAt(issuedAt);
            point.setTemperature(25f);
            point.setHumidity(60f);
            point.setSource("TEST");
            weatherRows.add(point);
        }
        when(weatherService.getForecast(any(), any())).thenReturn(weatherRows);
        ReflectionTestUtils.setField(predictService, "weatherForecastService", weatherService);

        predictService.forecast();

        verify(forecastRunService, atLeastOnce()).updateMetadata(argThat(run -> run.getDataCutoff().equals(hourlyRows(200).get(199).getTime())
                && run.getWeatherIssuedAt().equals(issuedAt)
                && run.getWeatherFallbackReason() == null
                && run.getArtifactChecksum().matches("[0-9a-f]{64}")));
    }

    @Test
    void shouldRecordWeatherFallbackReasonWhenWeatherIsUnavailable() {
        when(loadDataMapper.selectList(any())).thenReturn(hourlyRows(200));
        when(flaskInferenceService.forecast(any())).thenReturn(forecastResult());
        WeatherForecastService weatherService = mock(WeatherForecastService.class);
        when(weatherService.getForecast(any(), any())).thenReturn(List.of());
        ReflectionTestUtils.setField(predictService, "weatherForecastService", weatherService);

        predictService.forecast();

        verify(forecastRunService, atLeastOnce()).updateMetadata(argThat(run -> "WEATHER_FORECAST_UNAVAILABLE".equals(run.getWeatherFallbackReason())
                && run.getWeatherIssuedAt() == null));
    }

    private static FlaskInferenceService.ForecastResult forecastResult() {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 24; i++) values.add(900.0 + i * 5.0);
        return new FlaskInferenceService.ForecastResult(values, "torchscript");
    }

    private static List<LoadData> hourlyRows(int count) {
        List<LoadData> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDateTime time = LocalDateTime.of(2026, 7, 8, 0, 0).plusHours(i);
            LoadData data = new LoadData();
            data.setTime(time);
            data.setLoadMw(800f + i);
            data.setTemperature(25f);
            data.setHumidity(60f);
            data.setHour(time.getHour());
            data.setDayOfWeek(time.getDayOfWeek().getValue() % 7);
            data.setMonth(time.getMonthValue());
            data.setIsHoliday(0);
            rows.add(data);
        }
        return rows;
    }
}
