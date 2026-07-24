package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.common.GridTopologyConstants;
import com.powerload.dto.response.ForecastResponse;
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
import com.powerload.service.PredictService;
import com.powerload.service.WeatherForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictServiceImpl implements PredictService {

    private static final int WINDOW_HOURS = 200;
    private static final int MIN_HOURLY_POINTS = 168;
    private static final int FORECAST_HORIZON_HOURS = 24;
    private static final int NODE_CALIBRATION_WINDOW_HOURS = 24;
    private static final double NODE_FORECAST_MIN_RATIO = 0.65;
    private static final double NODE_FORECAST_MAX_RATIO = 1.35;

    private final LoadDataMapper loadDataMapper;
    private final PredictionResultMapper predictionResultMapper;
    private final FlaskInferenceService flaskInferenceService;
    private final ModelVersionMapper modelVersionMapper;
    private final ForecastRunService forecastRunService;

    @Autowired(required = false)
    private WeatherForecastService weatherForecastService;

    @Override
    @Transactional
    public ForecastResponse forecast() {
        return forecast(GridTopologyConstants.ROOT_NODE_ID, null);
    }

    @Override
    @Transactional
    public ForecastResponse forecast(Long nodeId) {
        return forecast(nodeId, null);
    }

    @Override
    @Transactional
    public ForecastResponse forecast(Long nodeId, String idempotencyKey) {
        Long targetNodeId = nodeId == null ? GridTopologyConstants.ROOT_NODE_ID : nodeId;
        LocalDateTime end = LocalDateTime.now();
        List<LoadData> rows = loadDataMapper.selectList(new LambdaQueryWrapper<LoadData>()
                .eq(LoadData::getNodeId, targetNodeId)
                .ge(LoadData::getTime, end.minusHours(WINDOW_HOURS))
                .le(LoadData::getTime, end)
                .apply("MINUTE(time) = 0 AND SECOND(time) = 0")
                .orderByAsc(LoadData::getTime));
        if (rows.size() < MIN_HOURLY_POINTS) {
            throw new IllegalStateException("数据不足: 至少需要 " + MIN_HOURLY_POINTS + " 条合法整点数据，实际 " + rows.size() + " 条。");
        }

        LocalDateTime issuedAt = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDateTime forecastStart = issuedAt.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        ForecastRun requested = new ForecastRun();
        requested.setRunId("FR-" + UUID.randomUUID());
        requested.setStatus("RUNNING");
        requested.setIssuedAt(issuedAt);
        requested.setDataCutoff(rows.get(rows.size() - 1).getTime());
        requested.setForecastHorizonHours(FORECAST_HORIZON_HOURS);
        requested.setNodeId(targetNodeId);
        requested.setArtifactChecksum("PENDING");
        requested.setWeatherFallbackReason("WEATHER_NOT_RESOLVED");
        requested.setPredictionCount(0);
        requested.setIdempotencyKey(normalizeIdempotencyKey(idempotencyKey));
        requested.setCreatedAt(issuedAt);
        ForecastRun run = forecastRunService.createOrReuseCompleted(requested);
        if ("COMPLETED".equals(run.getStatus())) {
            return completedResponse(run);
        }

        try {
            return executeForecast(targetNodeId, rows, forecastStart, run);
        } catch (RuntimeException e) {
            forecastRunService.markFailed(run.getId(), safeFailureReason(e));
            throw e;
        }
    }

    private ForecastResponse executeForecast(Long nodeId, List<LoadData> rows,
                                             LocalDateTime forecastStart, ForecastRun run) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<Map<String, Object>> rawData = rows.stream().map(data -> {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", data.getTime() == null ? null : data.getTime().format(formatter));
            point.put("load_mw", data.getLoadMw());
            point.put("temperature", data.getTemperature());
            point.put("humidity", data.getHumidity());
            point.put("is_holiday", data.getIsHoliday());
            point.put("hour", data.getHour());
            point.put("day_of_week", data.getDayOfWeek());
            point.put("month", data.getMonth());
            return point;
        }).collect(Collectors.toList());

        WeatherInput weather = futureWeather(forecastStart, FORECAST_HORIZON_HOURS);
        run.setWeatherIssuedAt(weather.issuedAt());
        run.setWeatherFallbackReason(weather.fallbackReason());
        forecastRunService.updateMetadata(run);
        FlaskInferenceService.ForecastResult inference = weather.points().isEmpty()
                ? flaskInferenceService.forecast(rawData)
                : flaskInferenceService.forecast(rawData, weather.points());
        List<Double> predictions = calibrateNodePredictions(nodeId, rows, inference.predictions());
        if (predictions == null || predictions.isEmpty()) {
            throw new IllegalStateException("PREDICTION_RESULT_EMPTY");
        }

        ModelVersion version = resolveRuntimeModelVersion(inference.model());
        Long modelVersionId = version == null ? null : version.getId();
        run.setModelVersionId(modelVersionId);
        run.setModelVersion(version == null
                ? (inference.model() == null ? "UNAVAILABLE" : inference.model())
                : version.getVersion());
        run.setArtifactChecksum(artifactChecksum(version));
        forecastRunService.updateMetadata(run);

        double intervalHalfWidth = version != null && version.getRmse() != null
                ? Math.max(1.0, version.getRmse() * 1.2816) : 0;
        List<Double> lowerBounds = new ArrayList<>();
        List<Double> upperBounds = new ArrayList<>();
        for (int i = 0; i < predictions.size(); i++) {
            Double predicted = predictions.get(i);
            if (predicted == null) {
                throw new IllegalStateException("PREDICTION_VALUE_INVALID");
            }
            PredictionResult result = new PredictionResult();
            result.setForecastRunId(run.getId());
            result.setLeadHours((int) ChronoUnit.HOURS.between(
                    run.getIssuedAt().truncatedTo(ChronoUnit.HOURS), forecastStart.plusHours(i)));
            result.setNodeId(nodeId);
            result.setPredictTime(forecastStart.plusHours(i));
            result.setPredictedLoad(predicted.floatValue());
            Float lower = intervalHalfWidth > 0 ? (float) Math.max(0, predicted - intervalHalfWidth) : null;
            Float upper = intervalHalfWidth > 0 ? (float) (predicted + intervalHalfWidth) : null;
            result.setLowerBound(lower);
            result.setUpperBound(upper);
            result.setModelVersionId(modelVersionId);
            result.setCreatedAt(run.getIssuedAt());
            if (predictionResultMapper.insert(result) != 1) {
                throw new IllegalStateException("PREDICTION_RESULT_PERSIST_FAILED");
            }
            if (lower != null) lowerBounds.add(lower.doubleValue());
            if (upper != null) upperBounds.add(upper.doubleValue());
        }
        forecastRunService.markCompleted(run.getId(), predictions.size(), LocalDateTime.now());

        ForecastResponse response = new ForecastResponse();
        response.setNodeId(nodeId);
        response.setSource("NODE_LEVEL_SIMULATION");
        response.setPredictions(predictions);
        response.setModel(inference.model());
        response.setForecastStartTime(forecastStart);
        response.setLowerBounds(lowerBounds.isEmpty() ? null : lowerBounds);
        response.setUpperBounds(upperBounds.isEmpty() ? null : upperBounds);
        response.setIntervalSource(intervalHalfWidth > 0 ? "VALIDATION_RMSE_REFERENCE" : "UNAVAILABLE");
        response.setModelVersionId(modelVersionId);
        response.setFutureWeatherAvailable(!weather.points().isEmpty());
        response.setWeatherSource(weather.points().isEmpty() ? null
                : String.valueOf(weather.points().get(0).getOrDefault("source", "OPEN_METEO")));
        response.setFutureWeatherApplied(inference.futureWeatherApplied());
        response.setFutureWeatherFallback(inference.futureWeatherFallback());
        applyTraceability(response, run, "TRACEABLE");
        return response;
    }

    private ForecastResponse completedResponse(ForecastRun run) {
        List<PredictionResult> results = predictionResultMapper.selectList(new LambdaQueryWrapper<PredictionResult>()
                .eq(PredictionResult::getForecastRunId, run.getId())
                .orderByAsc(PredictionResult::getPredictTime));
        ForecastResponse response = new ForecastResponse();
        response.setNodeId(run.getNodeId());
        response.setSource("NODE_LEVEL_SIMULATION");
        response.setPredictions(results.stream().map(item -> item.getPredictedLoad().doubleValue()).toList());
        response.setModel(run.getModelVersion());
        response.setModelVersionId(run.getModelVersionId());
        response.setForecastStartTime(results.isEmpty() ? null : results.get(0).getPredictTime());
        applyTraceability(response, run, "TRACEABLE");
        return response;
    }

    private void applyTraceability(ForecastResponse response, ForecastRun run, String status) {
        response.setRunId(run.getRunId());
        response.setIssuedAt(run.getIssuedAt());
        response.setDataCutoff(run.getDataCutoff());
        response.setForecastHorizonHours(run.getForecastHorizonHours());
        response.setModelVersion(run.getModelVersion());
        response.setArtifactChecksum(run.getArtifactChecksum());
        response.setWeatherIssuedAt(run.getWeatherIssuedAt());
        response.setTraceabilityStatus(status);
    }

    private List<Double> calibrateNodePredictions(Long nodeId, List<LoadData> rows, List<Double> rawPredictions) {
        if (nodeId == null || GridTopologyConstants.ROOT_NODE_ID == nodeId || rows == null || rows.isEmpty()
                || rawPredictions == null || rawPredictions.isEmpty()) {
            return rawPredictions;
        }
        int start = Math.max(0, rows.size() - NODE_CALIBRATION_WINDOW_HOURS);
        double recentSum = 0;
        int recentCount = 0;
        for (int i = start; i < rows.size(); i++) {
            Float load = rows.get(i).getLoadMw();
            if (load != null && load > 0) {
                recentSum += load;
                recentCount++;
            }
        }
        double recentMean = recentCount == 0 ? 0 : recentSum / recentCount;
        double predictionMean = rawPredictions.stream().filter(java.util.Objects::nonNull).filter(value -> value > 0)
                .mapToDouble(Double::doubleValue).average().orElse(0);
        if (recentMean <= 0 || predictionMean <= 0) return rawPredictions;
        double scale = recentMean / predictionMean;
        double lowerBound = recentMean * NODE_FORECAST_MIN_RATIO;
        double upperBound = recentMean * NODE_FORECAST_MAX_RATIO;
        return rawPredictions.stream().map(value -> value == null ? null
                : Math.min(upperBound, Math.max(lowerBound, Math.max(0, value * scale)))).toList();
    }

    private ModelVersion resolveRuntimeModelVersion(String runtimeModel) {
        String modelName = normalizeRuntimeModel(runtimeModel);
        if (modelName == null) return null;
        return modelVersionMapper.selectList(new LambdaQueryWrapper<ModelVersion>()
                        .eq(ModelVersion::getModelName, modelName)
                        .orderByDesc(ModelVersion::getIsActive)
                        .orderByDesc(ModelVersion::getTrainedAt)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    private WeatherInput futureWeather(LocalDateTime start, int horizon) {
        if (weatherForecastService == null) return new WeatherInput(List.of(), null, "WEATHER_SERVICE_UNAVAILABLE");
        try {
            List<WeatherForecast> rows = weatherForecastService.getForecast(start, start.plusHours(horizon - 1L));
            if (rows.isEmpty()) return new WeatherInput(List.of(), null, "WEATHER_FORECAST_UNAVAILABLE");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            List<Map<String, Object>> points = rows.stream().map(row -> {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("time", row.getForecastTime().format(formatter));
                point.put("temperature", row.getTemperature());
                point.put("humidity", row.getHumidity());
                point.put("source", row.getSource());
                return point;
            }).toList();
            boolean complete = rows.size() == horizon && rows.stream().map(WeatherForecast::getForecastTime)
                    .collect(Collectors.toSet()).size() == horizon;
            List<LocalDateTime> batches = rows.stream().map(WeatherForecast::getIssuedAt).distinct().toList();
            if (complete && batches.size() == 1 && batches.get(0) != null) {
                return new WeatherInput(points, batches.get(0), null);
            }
            return new WeatherInput(points, null, complete ? "WEATHER_BATCH_MIXED" : "WEATHER_FORECAST_INCOMPLETE");
        } catch (Exception e) {
            log.warn("future weather read failed; using model fallback", e);
            return new WeatherInput(List.of(), null, "WEATHER_FORECAST_READ_FAILED");
        }
    }

    private String artifactChecksum(ModelVersion version) {
        if (version == null || version.getFilePath() == null || version.getFilePath().isBlank()) return "UNAVAILABLE";
        try {
            byte[] bytes = Files.readAllBytes(Path.of(version.getFilePath()));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (Exception e) {
            return "UNAVAILABLE";
        }
    }

    private String normalizeRuntimeModel(String runtimeModel) {
        if (runtimeModel == null) return null;
        String normalized = runtimeModel.trim().toLowerCase(Locale.ROOT);
        if ("torchscript".equals(normalized) || "lstm".equals(normalized)) return "LSTM";
        if ("prophet".equals(normalized)) return "Prophet";
        return null;
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) return null;
        return key.trim().substring(0, Math.min(key.trim().length(), 128));
    }

    private String safeFailureReason(RuntimeException exception) {
        String reason = exception.getMessage();
        if (reason != null && reason.matches("PREDICTION_[A-Z_]+")) {
            return reason;
        }
        return "PREDICTION_EXECUTION_FAILED";
    }

    private record WeatherInput(List<Map<String, Object>> points, LocalDateTime issuedAt, String fallbackReason) {
    }
}
