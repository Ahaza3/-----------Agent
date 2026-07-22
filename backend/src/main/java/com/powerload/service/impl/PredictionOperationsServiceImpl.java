package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.common.GridTopologyConstants;
import com.powerload.entity.LoadData;
import com.powerload.entity.PredictionResult;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.mapper.PredictionResultMapper;
import com.powerload.service.PredictionOperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PredictionOperationsServiceImpl implements PredictionOperationsService {

    private static final int DEFAULT_DAYS = 7;

    private final LoadDataMapper loadDataMapper;
    private final PredictionResultMapper predictionResultMapper;

    @Override
    public Map<String, Object> quality(LocalDateTime start, LocalDateTime end) {
        LoadData latest = loadDataMapper.selectOne(new LambdaQueryWrapper<LoadData>()
                .eq(LoadData::getNodeId, GridTopologyConstants.ROOT_NODE_ID)
                .orderByDesc(LoadData::getTime)
                .last("LIMIT 1"));
        if (latest == null || latest.getTime() == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("status", "NO_DATA");
            empty.put("latestDataTime", null);
            empty.put("actualPoints", 0);
            empty.put("expectedPoints", 0);
            empty.put("missingPoints", 0);
            empty.put("continuityRate", 0.0);
            empty.put("weatherMissingPoints", 0);
            empty.put("weatherMissingRate", 0.0);
            empty.put("dataDelayMinutes", null);
            empty.put("sourceSummary", Map.of());
            return empty;
        }

        LocalDateTime effectiveEnd = alignHour(end != null ? end : latest.getTime());
        LocalDateTime effectiveStart = alignHour(start != null
                ? start : effectiveEnd.minusDays(DEFAULT_DAYS));
        if (effectiveStart.isAfter(effectiveEnd)) {
            LocalDateTime swap = effectiveStart;
            effectiveStart = effectiveEnd;
            effectiveEnd = swap;
        }

        List<LoadData> rows = loadDataMapper.selectList(new LambdaQueryWrapper<LoadData>()
                .eq(LoadData::getNodeId, GridTopologyConstants.ROOT_NODE_ID)
                .ge(LoadData::getTime, effectiveStart)
                .le(LoadData::getTime, effectiveEnd)
                .orderByAsc(LoadData::getTime));
        List<LocalDateTime> times = rows.stream()
                .map(LoadData::getTime)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        long expectedPoints = ChronoUnit.HOURS.between(effectiveStart, effectiveEnd) + 1;
        long actualPoints = times.size();
        long missingPoints = Math.max(0, expectedPoints - actualPoints);
        long maxGapMinutes = 0;
        for (int i = 1; i < times.size(); i++) {
            long gapMinutes = Duration.between(times.get(i - 1), times.get(i)).toMinutes();
            maxGapMinutes = Math.max(maxGapMinutes, gapMinutes);
        }

        long weatherMissingPoints = rows.stream()
                .filter(row -> row.getTemperature() == null || row.getHumidity() == null)
                .count();
        Map<String, Long> sourceSummary = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> row.getDataSource() == null || row.getDataSource().isBlank()
                                ? "UNKNOWN" : row.getDataSource(),
                        LinkedHashMap::new,
                        Collectors.counting()));

        LocalDateTime expectedLatestHour = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .minusHours(1);
        long delayMinutes = Math.max(0, Duration.between(latest.getTime(), expectedLatestHour).toMinutes());
        double continuityRate = expectedPoints == 0 ? 0 : actualPoints * 100.0 / expectedPoints;
        double weatherMissingRate = actualPoints == 0 ? 0 : weatherMissingPoints * 100.0 / actualPoints;
        String status = delayMinutes > 90
                ? "DELAYED"
                : missingPoints > 0 || weatherMissingPoints > 0 ? "WARNING" : "NORMAL";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("rangeStart", effectiveStart);
        result.put("rangeEnd", effectiveEnd);
        result.put("latestDataTime", latest.getTime());
        result.put("dataDelayMinutes", delayMinutes);
        result.put("actualPoints", actualPoints);
        result.put("expectedPoints", expectedPoints);
        result.put("missingPoints", missingPoints);
        result.put("maxGapMinutes", maxGapMinutes);
        result.put("continuityRate", round(continuityRate));
        result.put("weatherMissingPoints", weatherMissingPoints);
        result.put("weatherMissingRate", round(weatherMissingRate));
        result.put("sourceSummary", sourceSummary);
        return result;
    }

    @Override
    public Map<String, Object> review(LocalDateTime start, LocalDateTime end) {
        LocalDateTime effectiveEnd = end != null ? end : LocalDateTime.now();
        LocalDateTime effectiveStart = start != null
                ? start : effectiveEnd.minusDays(DEFAULT_DAYS);

        List<PredictionResult> predictions = predictionResultMapper.selectList(
                new LambdaQueryWrapper<PredictionResult>()
                        .eq(PredictionResult::getNodeId, GridTopologyConstants.ROOT_NODE_ID)
                        .ge(PredictionResult::getPredictTime, effectiveStart)
                        .le(PredictionResult::getPredictTime, effectiveEnd)
                        .orderByAsc(PredictionResult::getPredictTime)
                        .orderByDesc(PredictionResult::getCreatedAt));
        Map<LocalDateTime, PredictionResult> latestByTarget = new TreeMap<>();
        for (PredictionResult prediction : predictions) {
            if (prediction.getPredictTime() == null) continue;
            PredictionResult existing = latestByTarget.get(prediction.getPredictTime());
            if (existing == null || newerThan(prediction, existing.getCreatedAt())) {
                latestByTarget.put(prediction.getPredictTime(), prediction);
            }
        }

        List<LoadData> actualRows = loadDataMapper.selectList(new LambdaQueryWrapper<LoadData>()
                .eq(LoadData::getNodeId, GridTopologyConstants.ROOT_NODE_ID)
                .ge(LoadData::getTime, effectiveStart)
                .le(LoadData::getTime, effectiveEnd)
                .orderByAsc(LoadData::getTime));
        Map<LocalDateTime, Float> actualByTime = actualRows.stream()
                .filter(row -> row.getTime() != null && row.getLoadMw() != null)
                .collect(Collectors.toMap(
                        LoadData::getTime,
                        LoadData::getLoadMw,
                        (left, right) -> right));

        List<Map<String, Object>> series = new ArrayList<>();
        double absoluteErrorSum = 0;
        double squaredErrorSum = 0;
        double percentageErrorSum = 0;
        int evaluatedPoints = 0;
        double peakPredicted = Double.NEGATIVE_INFINITY;
        double peakActual = Double.NEGATIVE_INFINITY;
        LocalDateTime latestPredictionCreatedAt = null;
        Long modelVersionId = null;

        for (PredictionResult prediction : latestByTarget.values()) {
            Float actual = actualByTime.get(prediction.getPredictTime());
            if (actual == null || prediction.getPredictedLoad() == null) continue;
            double predicted = prediction.getPredictedLoad();
            double actualValue = actual;
            double error = predicted - actualValue;
            absoluteErrorSum += Math.abs(error);
            squaredErrorSum += error * error;
            percentageErrorSum += Math.abs(error / Math.max(Math.abs(actualValue), 1.0)) * 100;
            evaluatedPoints++;
            peakPredicted = Math.max(peakPredicted, predicted);
            peakActual = Math.max(peakActual, actualValue);
            if (newerThan(prediction, latestPredictionCreatedAt)) {
                latestPredictionCreatedAt = prediction.getCreatedAt();
                modelVersionId = prediction.getModelVersionId();
            }

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", prediction.getPredictTime());
            point.put("predicted", predicted);
            point.put("actual", actualValue);
            point.put("error", round(error));
            point.put("lowerBound", prediction.getLowerBound());
            point.put("upperBound", prediction.getUpperBound());
            point.put("traceabilityStatus", prediction.getForecastRunId() == null ? "LEGACY" : "TRACEABLE");
            series.add(point);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", evaluatedPoints >= 3 ? "READY" : "INSUFFICIENT_DATA");
        result.put("rangeStart", effectiveStart);
        result.put("rangeEnd", effectiveEnd);
        result.put("evaluatedPoints", evaluatedPoints);
        result.put("candidatePoints", latestByTarget.size());
        result.put("coverageRate", latestByTarget.isEmpty()
                ? 0.0 : round(evaluatedPoints * 100.0 / latestByTarget.size()));
        result.put("mae", evaluatedPoints == 0 ? null : round(absoluteErrorSum / evaluatedPoints));
        result.put("rmse", evaluatedPoints == 0 ? null : round(Math.sqrt(squaredErrorSum / evaluatedPoints)));
        result.put("mape", evaluatedPoints == 0 ? null : round(percentageErrorSum / evaluatedPoints));
        result.put("peakErrorMw", evaluatedPoints == 0 ? null : round(Math.abs(peakPredicted - peakActual)));
        result.put("latestPredictionCreatedAt", latestPredictionCreatedAt);
        result.put("modelVersionId", modelVersionId);
        result.put("series", series);
        return result;
    }

    private boolean newerThan(PredictionResult candidate, LocalDateTime timestamp) {
        return candidate.getCreatedAt() != null
                && (timestamp == null || candidate.getCreatedAt().isAfter(timestamp));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private LocalDateTime alignHour(LocalDateTime value) {
        return value.withMinute(0).withSecond(0).withNano(0);
    }
}
