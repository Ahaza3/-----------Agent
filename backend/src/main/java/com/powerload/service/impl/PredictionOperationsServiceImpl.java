package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.common.GridTopologyConstants;
import com.powerload.dto.response.FixedLeadReviewCandidate;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;
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
        return review(GridTopologyConstants.ROOT_NODE_ID, 24, start, end, null);
    }

    @Override
    public Map<String, Object> review(Long nodeId, int leadHour, LocalDateTime start, LocalDateTime end,
                                      String modelVersion) {
        if (leadHour != 1 && leadHour != 4 && leadHour != 24) {
            throw new IllegalArgumentException("leadHour 仅支持 1、4、24");
        }
        Long effectiveNodeId = nodeId == null ? GridTopologyConstants.ROOT_NODE_ID : nodeId;
        LocalDateTime effectiveEnd = alignHour(end == null ? LocalDateTime.now() : end);
        LocalDateTime effectiveStart = alignHour(start == null ? effectiveEnd.minusDays(DEFAULT_DAYS) : start);
        if (effectiveStart.isAfter(effectiveEnd)) throw new IllegalArgumentException("startTime 不能晚于 endTime");
        if (ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) > 31) {
            throw new IllegalArgumentException("评估时间范围不能超过 31 天");
        }

        List<FixedLeadReviewCandidate> candidates = predictionResultMapper.selectFixedLeadReviewCandidates(
                effectiveNodeId, leadHour, effectiveStart, effectiveEnd, modelVersion);
        if (candidates == null) candidates = List.of();
        List<LoadData> actualRows = loadDataMapper.selectList(new LambdaQueryWrapper<LoadData>()
                .eq(LoadData::getNodeId, effectiveNodeId).ge(LoadData::getTime, effectiveStart)
                .le(LoadData::getTime, effectiveEnd).orderByAsc(LoadData::getTime));
        Map<LocalDateTime, Float> actualByTime = new HashMap<>();
        for (LoadData row : actualRows) {
            if (row.getTime() != null && row.getLoadMw() != null && row.getLoadMw() >= 0) actualByTime.put(row.getTime(), row.getLoadMw());
        }
        List<ReviewSample> samples = new ArrayList<>();
        int excluded = 0;
        for (FixedLeadReviewCandidate candidate : candidates) {
            Float actual = candidate.getPredictTime() == null ? null : actualByTime.get(candidate.getPredictTime());
            if (actual == null || candidate.getPredictedLoad() == null || candidate.getPredictedLoad() < 0) { excluded++; continue; }
            samples.add(new ReviewSample(candidate.getPredictTime(), candidate.getPredictedLoad(), actual, candidate.getModelVersion()));
        }
        return calculateReview(effectiveNodeId, leadHour, effectiveStart, effectiveEnd, candidates.size(), excluded, samples);
    }

    private Map<String, Object> calculateReview(Long nodeId, int leadHour, LocalDateTime start, LocalDateTime end,
                                                 int candidateCount, int excluded, List<ReviewSample> samples) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeId", nodeId); result.put("leadHour", leadHour);
        result.put("startTime", start); result.put("endTime", end);
        result.put("rangeStart", start); result.put("rangeEnd", end);
        result.put("candidatePoints", candidateCount); result.put("sampleCount", samples.size());
        result.put("evaluatedPoints", samples.size()); result.put("excludedSampleCount", excluded);
        result.put("dataSource", "TRACEABLE_COMPLETED_FORECAST_RUN");
        result.put("legacyDataIncluded", false);
        result.put("modelVersions", samples.stream().map(ReviewSample::modelVersion).filter(Objects::nonNull).distinct().sorted().toList());
        if (samples.isEmpty()) {
            result.put("calculationStatus", "NO_SAMPLES"); result.put("status", "INSUFFICIENT_DATA");
            result.put("unavailableReason", "没有满足固定提前量且已回填实际值的样本");
            putNullMetrics(result); result.put("series", List.of()); return result;
        }
        double abs = 0, squared = 0, percent = 0, actualAbs = 0;
        int mapeCount = 0, underForecastCount = 0;
        List<Double> actualValues = new ArrayList<>(); List<Map<String, Object>> series = new ArrayList<>();
        for (ReviewSample sample : samples) {
            double error = sample.predicted() - sample.actual(); abs += Math.abs(error); squared += error * error; actualAbs += Math.abs(sample.actual());
            if (sample.actual() != 0) { percent += Math.abs(error / sample.actual()) * 100; mapeCount++; }
            actualValues.add(sample.actual());
            Map<String, Object> point = new LinkedHashMap<>(); point.put("time", sample.time()); point.put("predicted", sample.predicted()); point.put("actual", sample.actual()); point.put("error", round(error)); point.put("traceabilityStatus", "TRACEABLE"); series.add(point);
        }
        actualValues.sort(Comparator.naturalOrder()); double p90 = actualValues.get((int) Math.ceil(actualValues.size() * .9) - 1);
        for (ReviewSample sample : samples) if (sample.actual() >= p90 && sample.predicted() < sample.actual()) underForecastCount++;
        long highLoadCount = samples.stream().filter(sample -> sample.actual() >= p90).count();
        ReviewSample actualPeak = samples.stream().sorted(Comparator.comparingDouble(ReviewSample::actual).reversed().thenComparing(ReviewSample::time)).findFirst().orElseThrow();
        ReviewSample predictedPeak = samples.stream().sorted(Comparator.comparingDouble(ReviewSample::predicted).reversed().thenComparing(ReviewSample::time)).findFirst().orElseThrow();
        result.put("calculationStatus", "READY"); result.put("status", samples.size() >= 3 ? "READY" : "INSUFFICIENT_DATA");
        result.put("unavailableReason", actualAbs == 0 ? "WMAPE 分母为 0" : null);
        result.put("mae", round(abs / samples.size())); result.put("rmse", round(Math.sqrt(squared / samples.size())));
        result.put("mape", mapeCount == 0 ? null : round(percent / mapeCount)); result.put("mapeExcludedZeroActualCount", samples.size() - mapeCount);
        result.put("wmape", actualAbs == 0 ? null : round(abs / actualAbs * 100));
        result.put("highLoadThresholdMw", p90); result.put("highLoadThresholdSource", "P90_ACTUAL"); result.put("highLoadSampleCount", highLoadCount); result.put("underForecastCount", underForecastCount);
        result.put("highLoadUnderForecastRate", highLoadCount == 0 ? null : round(underForecastCount * 100.0 / highLoadCount));
        result.put("actualPeakTime", actualPeak.time()); result.put("predictedPeakTime", predictedPeak.time()); result.put("actualPeakLoadMw", actualPeak.actual()); result.put("predictedPeakLoadMw", predictedPeak.predicted());
        result.put("peakTimeErrorHours", Math.abs(ChronoUnit.HOURS.between(actualPeak.time(), predictedPeak.time())));
        result.put("coverageRate", candidateCount == 0 ? 0.0 : round(samples.size() * 100.0 / candidateCount)); result.put("series", series);
        return result;
    }

    private void putNullMetrics(Map<String, Object> result) {
        for (String key : List.of("mae", "rmse", "mape", "wmape", "peakTimeErrorHours", "highLoadUnderForecastRate", "highLoadThresholdMw", "actualPeakTime", "predictedPeakTime", "actualPeakLoadMw", "predictedPeakLoadMw")) result.put(key, null);
        result.put("highLoadThresholdSource", "P90_ACTUAL"); result.put("highLoadSampleCount", 0); result.put("underForecastCount", 0); result.put("mapeExcludedZeroActualCount", 0);
    }

    private record ReviewSample(LocalDateTime time, double predicted, double actual, String modelVersion) {}

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
