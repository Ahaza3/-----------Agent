package com.powerload.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import com.powerload.entity.PredictionResult;
import com.powerload.mapper.PredictionResultMapper;
import com.powerload.service.PredictService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 预测查询工具 — 查询最新一批 24h 预测数据，或触发一次预测生成。
 *
 * <p>数据来源 MOCK_FORECAST（LSTM 模拟训练结果），非真实调度预测。</p>
 */
@Slf4j
@Component
public class QueryForecastTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PredictionResultMapper predictionResultMapper;
    private final PredictService predictService;

    public QueryForecastTool(PredictionResultMapper predictionResultMapper,
                             PredictService predictService) {
        this.predictionResultMapper = predictionResultMapper;
        this.predictService = predictService;
    }

    @Override
    public String name() {
        return "query_forecast";
    }

    @Override
    public String description() {
        return "查询未来 24 小时的负荷预测数据，包括每个小时的预测值、峰值时间、平均值等。" +
               "适用于用户询问'未来负荷趋势'、'预测最高负荷'、'明天负荷会到多少'等问题。" +
               "注意：预测数据来自 LSTM 模型模拟训练，标记为 MOCK_FORECAST，仅供演示参考。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "refresh", Map.of("type", "boolean",
                        "description", "是否强制重新生成预测，默认 false（从数据库读取最新批次）")
        ));
        return schema;
    }

    @Override
    public ToolResult execute(String argumentsJson) {
        boolean refresh = false;
        if (argumentsJson != null && !argumentsJson.isBlank()) {
            try {
                JsonNode args = MAPPER.readTree(argumentsJson);
                refresh = args.has("refresh") && args.get("refresh").asBoolean(false);
            } catch (JsonProcessingException e) {
                // 忽略无效参数，使用默认值
            }
        }

        try {
            if (refresh) {
                log.info("强制刷新预测...");
                predictService.forecast();
            }

            // 查询最新一批预测（createdAt 最大的一组记录）
            var latestRecord = getLatestCreatedAt();
            if (latestRecord == null) {
                // 预测表为空，尝试生成
                log.info("预测表为空，自动触发一次预测生成");
                predictService.forecast();
                latestRecord = getLatestCreatedAt();
                if (latestRecord == null) {
                    return ToolResult.fail("预测数据不可用：预测生成后仍未找到数据，请检查 Flask 预测服务是否正常。");
                }
            }

            LocalDateTime batchTime = latestRecord.getCreatedAt();
            var wrapper = new LambdaQueryWrapper<PredictionResult>()
                    .eq(PredictionResult::getCreatedAt, batchTime)
                    .orderByAsc(PredictionResult::getPredictTime);
            List<PredictionResult> batch = predictionResultMapper.selectList(wrapper);

            if (batch.isEmpty()) {
                return ToolResult.fail("预测批次为空");
            }

            // 构建结构化数据
            List<Map<String, Object>> forecastPoints = new ArrayList<>();
            for (PredictionResult pr : batch) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("time", pr.getPredictTime() != null ? pr.getPredictTime().toString() : null);
                point.put("predictedLoad", pr.getPredictedLoad());
                forecastPoints.add(point);
            }

            double maxLoad = batch.stream().mapToDouble(p ->
                    p.getPredictedLoad() != null ? p.getPredictedLoad() : 0).max().orElse(0);
            double minLoad = batch.stream().mapToDouble(p ->
                    p.getPredictedLoad() != null ? p.getPredictedLoad() : 0).min().orElse(0);
            double avgLoad = batch.stream().mapToDouble(p ->
                    p.getPredictedLoad() != null ? p.getPredictedLoad() : 0).average().orElse(0);

            PredictionResult peak = batch.stream().max(Comparator.comparing(
                    PredictionResult::getPredictedLoad, Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(null);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", "MOCK_FORECAST");
            result.put("model", "LSTM");
            result.put("modelVersionId", batch.get(0).getModelVersionId());
            result.put("forecastStartTime", batch.get(0).getPredictTime().toString());
            result.put("totalPoints", batch.size());
            result.put("maxLoadMw", (float) maxLoad);
            result.put("minLoadMw", (float) minLoad);
            result.put("avgLoadMw", (float) avgLoad);
            result.put("peakTime", peak != null && peak.getPredictTime() != null
                    ? peak.getPredictTime().toString() : null);
            result.put("forecastPoints", forecastPoints);

            // 构建 ECharts 折线图配置
            Map<String, Object> chartOption = buildChartOption(forecastPoints);

            String summary = String.format(
                    "未来24小时预测（LSTM模型，模拟数据）：峰值 %.1f MW（%s），谷值 %.1f MW，均值 %.1f MW。注意：这是 MOCK_FORECAST 模拟预测数据，仅供演示参考，不代表真实电网调度预测。",
                    maxLoad,
                    peak != null && peak.getPredictTime() != null
                            ? peak.getPredictTime().toLocalTime().toString() : "N/A",
                    minLoad, avgLoad);

            ToolResult toolResult = ToolResult.ok(summary, result);
            toolResult.setChart(chartOption);
            Map<String, Object> provenance = new LinkedHashMap<>();
            provenance.put("source", "prediction_result");
            provenance.put("model", "LSTM");
            provenance.put("modelVersionId", batch.get(0).getModelVersionId());
            provenance.put("simulated", true);
            provenance.put("forecastStartTime", batch.get(0).getPredictTime().toString());
            toolResult.setProvenance(provenance);
            return toolResult;

        } catch (Exception e) {
            log.error("查询预测失败", e);
            return ToolResult.fail("预测查询失败: " + e.getMessage());
        }
    }

    private PredictionResult getLatestCreatedAt() {
        var wrapper = new LambdaQueryWrapper<PredictionResult>()
                .orderByDesc(PredictionResult::getCreatedAt)
                .last("LIMIT 1");
        return predictionResultMapper.selectOne(wrapper);
    }

    /** 构建 ECharts 折线图配置 */
    private Map<String, Object> buildChartOption(List<Map<String, Object>> forecastPoints) {
        List<String> times = new ArrayList<>();
        List<Float> loads = new ArrayList<>();
        for (Map<String, Object> p : forecastPoints) {
            Object t = p.get("time");
            Object l = p.get("predictedLoad");
            times.add(t != null ? t.toString() : "");
            loads.add(l instanceof Number n ? n.floatValue() : 0f);
        }

        Map<String, Object> tooltip = new LinkedHashMap<>();
        tooltip.put("trigger", "axis");

        Map<String, Object> xAxis = new LinkedHashMap<>();
        xAxis.put("type", "category");
        xAxis.put("data", times);

        Map<String, Object> yAxis = new LinkedHashMap<>();
        yAxis.put("type", "value");
        yAxis.put("name", "MW");

        Map<String, Object> series = new LinkedHashMap<>();
        series.put("name", "预测负荷");
        series.put("type", "line");
        series.put("data", loads);
        series.put("smooth", true);
        series.put("lineStyle", Map.of("color", "#E6C300", "type", "dashed"));

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("tooltip", tooltip);
        option.put("xAxis", xAxis);
        option.put("yAxis", yAxis);
        option.put("series", List.of(series));
        return option;
    }
}
