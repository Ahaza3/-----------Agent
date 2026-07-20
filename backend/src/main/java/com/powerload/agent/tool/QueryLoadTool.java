package com.powerload.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import com.powerload.entity.LoadData;
import com.powerload.service.LoadDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 历史负荷查询工具 — 查询指定时间范围的负荷数据，支持等间隔采样和图表输出。
 */
@Slf4j
@Component
public class QueryLoadTool implements Tool {

    private static final ZoneId ASIA_SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_MAX_POINTS = 100;
    private static final int MAX_POINTS = 200;
    private static final long MAX_RANGE_DAYS = 31;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final LoadDataService loadDataService;

    public QueryLoadTool(LoadDataService loadDataService) {
        this.loadDataService = loadDataService;
    }

    @Override
    public String name() {
        return "query_load";
    }

    @Override
    public String description() {
        return "查询指定时间范围内的历史负荷数据（小时级），返回负荷值 MW、温度℃、湿度%。" +
               "适合绘制趋势图和回答诸如'昨天负荷高峰'、'本周趋势'等问题。" +
               "最多查询31天，数据可能超过maxPoints时会等间隔采样。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> startTime = new LinkedHashMap<>();
        startTime.put("type", "string");
        startTime.put("description", "ISO 8601 开始时间，包含时区（如 2026-07-15T00:00:00+08:00）");
        props.put("startTime", startTime);

        Map<String, Object> endTime = new LinkedHashMap<>();
        endTime.put("type", "string");
        endTime.put("description", "ISO 8601 结束时间，包含时区");
        props.put("endTime", endTime);

        Map<String, Object> maxPoints = new LinkedHashMap<>();
        maxPoints.put("type", "integer");
        maxPoints.put("description", "最大返回点数，默认 100，最大 200");
        props.put("maxPoints", maxPoints);

        schema.put("properties", props);
        schema.put("required", List.of("startTime", "endTime"));
        return schema;
    }

    @Override
    public ToolResult execute(String argumentsJson) {
        JsonNode args;
        try {
            args = MAPPER.readTree(argumentsJson);
        } catch (JsonProcessingException e) {
            return ToolResult.fail("参数 JSON 解析失败: " + e.getMessage());
        }

        String startStr = args.has("startTime") ? args.get("startTime").asText() : null;
        String endStr = args.has("endTime") ? args.get("endTime").asText() : null;

        if (startStr == null || endStr == null) {
            return ToolResult.fail("缺少必填参数: startTime 或 endTime");
        }

        ZonedDateTime startZdt, endZdt;
        try {
            startZdt = ZonedDateTime.parse(startStr, ISO_FMT);
        } catch (DateTimeParseException e) {
            try {
                startZdt = LocalDateTime.parse(startStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .atZone(ASIA_SHANGHAI);
            } catch (DateTimeParseException e2) {
                return ToolResult.fail("时间格式错误，请使用 ISO 8601 格式，如 2026-07-15T00:00:00+08:00");
            }
        }
        try {
            endZdt = ZonedDateTime.parse(endStr, ISO_FMT);
        } catch (DateTimeParseException e) {
            try {
                endZdt = LocalDateTime.parse(endStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .atZone(ASIA_SHANGHAI);
            } catch (DateTimeParseException e2) {
                return ToolResult.fail("时间格式错误，请使用 ISO 8601 格式，如 2026-07-15T00:00:00+08:00");
            }
        }

        LocalDateTime start = startZdt.withZoneSameInstant(ASIA_SHANGHAI).toLocalDateTime();
        LocalDateTime end = endZdt.withZoneSameInstant(ASIA_SHANGHAI).toLocalDateTime();

        if (!start.isBefore(end)) {
            return ToolResult.fail("startTime 必须早于 endTime");
        }

        if (Duration.between(start, end).toDays() > MAX_RANGE_DAYS) {
            return ToolResult.fail("查询范围不能超过 " + MAX_RANGE_DAYS + " 天");
        }

        int maxPoints = DEFAULT_MAX_POINTS;
        if (args.has("maxPoints")) {
            maxPoints = args.get("maxPoints").asInt(DEFAULT_MAX_POINTS);
            if (maxPoints < 1) maxPoints = 1;
            if (maxPoints > MAX_POINTS) maxPoints = MAX_POINTS;
        }

        List<LoadData> raw = loadDataService.queryRange(start, end);
        int totalPoints = raw.size();

        List<LoadData> sampled = sample(raw, maxPoints);

        List<Map<String, Object>> dataPoints = sampled.stream().map(d -> {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", d.getTime().toString());
            point.put("loadMw", d.getLoadMw());
            point.put("temperature", d.getTemperature());
            point.put("humidity", d.getHumidity());
            return point;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "MOCK_HISTORY");
        result.put("totalPoints", totalPoints);
        result.put("returnedPoints", dataPoints.size());
        result.put("sampled", totalPoints > maxPoints);
        result.put("startTime", start.toString());
        result.put("endTime", end.toString());
        result.put("dataPoints", dataPoints);

        // 构建 ECharts 折线图配置
        Map<String, Object> chartOption = buildChartOption(dataPoints);

        String summary = String.format("返回 %d 条数据点（共 %d 条，采样=%s）",
                dataPoints.size(), totalPoints, totalPoints > maxPoints);

        ToolResult toolResult = ToolResult.ok(summary, result);
        toolResult.setChart(chartOption);
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("source", "load_data");
        provenance.put("startTime", start.toString());
        provenance.put("endTime", end.toString());
        provenance.put("simulated", raw.stream().allMatch(item -> item.getDataSource() == null
                || item.getDataSource().contains("MOCK")));
        toolResult.setProvenance(provenance);
        return toolResult;
    }

    /** 等间隔采样 */
    private List<LoadData> sample(List<LoadData> raw, int maxPoints) {
        if (raw.isEmpty() || raw.size() <= maxPoints) {
            return new ArrayList<>(raw);
        }
        List<LoadData> sampled = new ArrayList<>(maxPoints);
        if (maxPoints == 1) {
            sampled.add(raw.get(0));
            return sampled;
        }
        double step = (double) (raw.size() - 1) / (maxPoints - 1);
        for (int i = 0; i < maxPoints; i++) {
            int idx = (int) Math.round(i * step);
            if (idx >= raw.size()) idx = raw.size() - 1;
            sampled.add(raw.get(idx));
        }
        return sampled;
    }

    /** 构建 ECharts 折线图配置 */
    private Map<String, Object> buildChartOption(List<Map<String, Object>> dataPoints) {
        List<String> times = new ArrayList<>();
        List<Float> loads = new ArrayList<>();
        for (Map<String, Object> p : dataPoints) {
            Object timeObj = p.get("time");
            Object loadObj = p.get("loadMw");
            times.add(timeObj != null ? timeObj.toString() : "");
            loads.add(loadObj instanceof Number n ? n.floatValue() : 0f);
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
        series.put("name", "负荷 (MW)");
        series.put("type", "line");
        series.put("data", loads);
        series.put("smooth", true);
        Map<String, Object> lineStyle = new LinkedHashMap<>();
        lineStyle.put("color", "#FF2A2A");
        series.put("lineStyle", lineStyle);

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("tooltip", tooltip);
        option.put("xAxis", xAxis);
        option.put("yAxis", yAxis);
        option.put("series", List.of(series));
        return option;
    }
}
