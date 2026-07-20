package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.dto.response.ForecastResponse;
import com.powerload.entity.LoadData;
import com.powerload.entity.ModelVersion;
import com.powerload.entity.PredictionResult;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.mapper.ModelVersionMapper;
import com.powerload.mapper.PredictionResultMapper;
import com.powerload.ml.FlaskInferenceService;
import com.powerload.service.PredictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    /** 上下文窗口：期望至少 168 个合法整点，最多取 200 个 */
    private static final int WINDOW_HOURS = 200;
    private static final int MIN_HOURLY_POINTS = 168;

    @Override
    @Transactional
    public ForecastResponse forecast() {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(WINDOW_HOURS);

        // 1. 只查询合法整点历史数据（minute=0, second=0），升序
        LambdaQueryWrapper<LoadData> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(LoadData::getTime, start)
               .le(LoadData::getTime, end)
               .apply("MINUTE(time) = 0 AND SECOND(time) = 0")
               .orderByAsc(LoadData::getTime);
        List<LoadData> rows = loadDataMapper.selectList(wrapper);

        log.debug("预测输入: {} 条整点数据 ({} ~ {})", rows.size(),
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
        FlaskInferenceService.ForecastResult inference = flaskInferenceService.forecast(rawData);
        List<Double> predictions = inference.predictions();
        String runtimeModel = inference.model();
        Long modelVersionId = resolveRuntimeModelVersionId(runtimeModel);

        // 4. 预测起点：当前整点的下一小时
        LocalDateTime forecastStart = LocalDateTime.now()
                .withMinute(0).withSecond(0).withNano(0).plusHours(1);

        // 5. 持久化 24 条同批次记录
        LocalDateTime batchTime = LocalDateTime.now();
        for (int i = 0; i < predictions.size(); i++) {
            PredictionResult pr = new PredictionResult();
            pr.setPredictTime(forecastStart.plusHours(i));
            pr.setPredictedLoad(predictions.get(i).floatValue());
            pr.setLowerBound(null);  // 无可靠置信区间，不伪造
            pr.setUpperBound(null);
            pr.setModelVersionId(modelVersionId);
            pr.setCreatedAt(batchTime);
            predictionResultMapper.insert(pr);
        }
        log.info("预测已持久化: {} 条记录, batch={}", predictions.size(), batchTime);

        // 6. 封装响应
        ForecastResponse response = new ForecastResponse();
        response.setPredictions(predictions);
        response.setModel(runtimeModel);
        response.setForecastStartTime(forecastStart);

        double minP = predictions.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double maxP = predictions.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        log.debug("预测完成: {} 个值, 起点={}, 范围 [{}, {}]",
                predictions.size(), forecastStart, minP, maxP);
        return response;
    }

    private Long resolveRuntimeModelVersionId(String runtimeModel) {
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
        return version == null ? null : version.getId();
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
