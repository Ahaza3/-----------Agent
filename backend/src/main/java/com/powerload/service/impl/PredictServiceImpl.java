package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.dto.response.ForecastResponse;
import com.powerload.entity.LoadData;
import com.powerload.entity.ModelVersion;
import com.powerload.entity.PredictionResult;
import com.powerload.entity.WeatherForecast;
import com.powerload.common.GridTopologyConstants;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.mapper.ModelVersionMapper;
import com.powerload.mapper.PredictionResultMapper;
import com.powerload.ml.FlaskInferenceService;
import com.powerload.service.PredictService;
import com.powerload.service.WeatherForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 负荷预测服务实现
 *
 * <p>查询最近合法整点历史数据 → 通过 Flask 推理 → 返回 24h 预测 + 持久化到 prediction_result。
 * 每次成功预测写入 24 条同批次记录（共享 createdAt），保留历史批次不做清表。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PredictServiceImpl implements PredictService {

    private final LoadDataMapper loadDataMapper;
    private final PredictionResultMapper predictionResultMapper;
    private final FlaskInferenceService flaskInferenceService;
    private final ModelVersionMapper modelVersionMapper;

    @Autowired(required = false)
    private WeatherForecastService weatherForecastService;

    /** 上下文窗口：期望至少 168 个合法整点，最多取 200 个 */
    private static final int WINDOW_HOURS = 200;
    private static final int MIN_HOURLY_POINTS = 168;
    private static final int NODE_CALIBRATION_WINDOW_HOURS = 24;
    private static final double NODE_FORECAST_MIN_RATIO = 0.65;
    private static final double NODE_FORECAST_MAX_RATIO = 1.35;

    @Override
    @Transactional
    public ForecastResponse forecast() {
        return forecast(GridTopologyConstants.ROOT_NODE_ID);
    }

    @Override
    @Transactional
    public ForecastResponse forecast(Long nodeId) {
        Long targetNodeId = nodeId != null ? nodeId : GridTopologyConstants.ROOT_NODE_ID;
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(WINDOW_HOURS);

        // 1. 只查询合法整点历史数据（minute=0, second=0），升序
        LambdaQueryWrapper<LoadData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LoadData::getNodeId, targetNodeId)
               .ge(LoadData::getTime, start)
               .le(LoadData::getTime, end)
               .apply("MINUTE(time) = 0 AND SECOND(time) = 0")
               .orderByAsc(LoadData::getTime);
        List<LoadData> rows = loadDataMapper.selectList(wrapper);

        log.debug("预测输入: nodeId={}, {} 条整点数据 ({} ~ {})", targetNodeId, rows.size(),
                rows.isEmpty() ? "N/A" : rows.get(0).getTime(),
                rows.isEmpty() ? "N/A" : rows.get(rows.size() - 1).getTime());

        if (rows.size() < MIN_HOURLY_POINTS) {
            throw new IllegalStateException(
                    String.format("数据不足: 至少需要 %d 条合法整点数据，实际 %d 条。请等待系统积累更多数据。",
                            MIN_HOURLY_POINTS, rows.size()));
        }

        // 2. 转换为 Flask 需要的格式
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<Map<String, Object>> rawData = rows.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", d.getTime() != null ? d.getTime().format(fmt) : null);
            m.put("load_mw", d.getLoadMw());
            m.put("temperature", d.getTemperature());
            m.put("humidity", d.getHumidity());
            m.put("is_holiday", d.getIsHoliday());
            m.put("hour", d.getHour());
            m.put("day_of_week", d.getDayOfWeek());
            m.put("month", d.getMonth());
            return m;
        }).collect(Collectors.toList());

        // 3. 调用 Flask 推理
        LocalDateTime forecastStart = LocalDateTime.now()
                .withMinute(0).withSecond(0).withNano(0).plusHours(1);
        List<Map<String, Object>> futureWeather = futureWeather(forecastStart, 24);
        FlaskInferenceService.ForecastResult inference = futureWeather.isEmpty()
                ? flaskInferenceService.forecast(rawData)
                : flaskInferenceService.forecast(rawData, futureWeather);
        List<Double> predictions = calibrateNodePredictions(
                targetNodeId, rows, inference.predictions());
        String runtimeModel = inference.model();
        ModelVersion modelVersion = resolveRuntimeModelVersion(runtimeModel);
        Long modelVersionId = modelVersion == null ? null : modelVersion.getId();
        double intervalHalfWidth = modelVersion != null && modelVersion.getRmse() != null
                ? Math.max(1.0, modelVersion.getRmse() * 1.2816)
                : 0;
        List<Double> lowerBounds = new ArrayList<>();
        List<Double> upperBounds = new ArrayList<>();

        // 4. 预测起点：当前整点的下一小时
        // 5. 持久化 24 条同批次记录
        LocalDateTime batchTime = LocalDateTime.now();
        for (int i = 0; i < predictions.size(); i++) {
            PredictionResult pr = new PredictionResult();
            pr.setNodeId(targetNodeId);
            pr.setPredictTime(forecastStart.plusHours(i));
            pr.setPredictedLoad(predictions.get(i).floatValue());
            Float lower = intervalHalfWidth > 0
                    ? (float) Math.max(0, predictions.get(i) - intervalHalfWidth) : null;
            Float upper = intervalHalfWidth > 0
                    ? (float) (predictions.get(i) + intervalHalfWidth) : null;
            pr.setLowerBound(lower);
            pr.setUpperBound(upper);
            pr.setModelVersionId(modelVersionId);
            pr.setCreatedAt(batchTime);
            predictionResultMapper.insert(pr);
            if (lower != null) lowerBounds.add(lower.doubleValue());
            if (upper != null) upperBounds.add(upper.doubleValue());
        }
        log.info("预测已持久化: {} 条记录, batch={}", predictions.size(), batchTime);

        // 6. 封装响应
        ForecastResponse response = new ForecastResponse();
        response.setNodeId(targetNodeId);
        response.setSource("NODE_LEVEL_SIMULATION");
        response.setPredictions(predictions);
        response.setModel(runtimeModel);
        response.setForecastStartTime(forecastStart);
        response.setLowerBounds(lowerBounds.isEmpty() ? null : lowerBounds);
        response.setUpperBounds(upperBounds.isEmpty() ? null : upperBounds);
        response.setIntervalSource(intervalHalfWidth > 0
                ? "VALIDATION_RMSE_REFERENCE" : "UNAVAILABLE");
        response.setModelVersionId(modelVersionId);
        response.setFutureWeatherAvailable(!futureWeather.isEmpty());
        response.setWeatherSource(futureWeather.isEmpty()
                ? null : String.valueOf(futureWeather.get(0).getOrDefault("source", "OPEN_METEO")));
        response.setFutureWeatherApplied(inference.futureWeatherApplied());
        response.setFutureWeatherFallback(inference.futureWeatherFallback());

        double minP = predictions.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double maxP = predictions.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        log.debug("预测完成: {} 个值, 起点={}, 范围 [{}, {}]",
                predictions.size(), forecastStart, minP, maxP);
        return response;
    }

    /**
     * 当前模型是在根区域量级数据上训练的，直接用于馈线会把绝对值预测到根区域量级。
     * 节点预测保留模型给出的变化形状，但按本节点最近 24 小时均值校准量级，
     * 并限制在合理的模拟波动范围内。根区域预测保持模型原值。
     */
    private List<Double> calibrateNodePredictions(Long nodeId,
                                                   List<LoadData> rows,
                                                   List<Double> rawPredictions) {
        if (nodeId == null
                || GridTopologyConstants.ROOT_NODE_ID == nodeId
                || rows == null
                || rows.isEmpty()
                || rawPredictions == null
                || rawPredictions.isEmpty()) {
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

        double predictionSum = rawPredictions.stream()
                .filter(java.util.Objects::nonNull)
                .filter(value -> value > 0)
                .mapToDouble(Double::doubleValue)
                .sum();
        long predictionCount = rawPredictions.stream()
                .filter(java.util.Objects::nonNull)
                .filter(value -> value > 0)
                .count();
        double predictionMean = predictionCount == 0 ? 0 : predictionSum / predictionCount;
        if (recentMean <= 0 || predictionMean <= 0) {
            return rawPredictions;
        }

        double scale = recentMean / predictionMean;
        double lowerBound = recentMean * NODE_FORECAST_MIN_RATIO;
        double upperBound = recentMean * NODE_FORECAST_MAX_RATIO;
        return rawPredictions.stream()
                .map(value -> {
                    if (value == null) {
                        return null;
                    }
                    double calibrated = Math.max(0, value * scale);
                    return Math.min(upperBound, Math.max(lowerBound, calibrated));
                })
                .toList();
    }

    private ModelVersion resolveRuntimeModelVersion(String runtimeModel) {
        String modelName = normalizeRuntimeModel(runtimeModel);
        if (modelName == null) {
            return null;
        }
        var version = modelVersionMapper.selectList(
                new LambdaQueryWrapper<ModelVersion>()
                        .eq(ModelVersion::getModelName, modelName)
                        .orderByDesc(ModelVersion::getIsActive)
                        .orderByDesc(ModelVersion::getTrainedAt)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);
        return version;
    }

    private List<Map<String, Object>> futureWeather(LocalDateTime start, int horizon) {
        if (weatherForecastService == null) return List.of();
        try {
            List<WeatherForecast> rows = weatherForecastService.getForecast(
                    start, start.plusHours(horizon - 1L));
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return rows.stream().map(row -> {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("time", row.getForecastTime().format(fmt));
                point.put("temperature", row.getTemperature());
                point.put("humidity", row.getHumidity());
                point.put("source", row.getSource());
                return point;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("未来天气读取失败，使用无天气输入预测: {}", e.getMessage());
            return List.of();
        }
    }

    private String normalizeRuntimeModel(String runtimeModel) {
        if (runtimeModel == null) {
            return null;
        }
        String normalized = runtimeModel.trim().toLowerCase(Locale.ROOT);
        if ("torchscript".equals(normalized) || "lstm".equals(normalized)) {
            return "LSTM";
        }
        if ("prophet".equals(normalized)) {
            return "Prophet";
        }
        return null;
    }
}
