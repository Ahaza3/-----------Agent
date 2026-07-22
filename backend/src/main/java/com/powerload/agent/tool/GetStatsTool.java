package com.powerload.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import com.powerload.dto.response.LoadStats;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.AlertEvent;
import com.powerload.service.AlertEventService;
import com.powerload.service.LoadDataService;
import com.powerload.service.RealtimeLoadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 实时负荷与统计工具 — 查询当前实时负荷、指定区间统计、最近告警概况。
 */
@Slf4j
@Component
public class GetStatsTool implements Tool {

    private static final ZoneId ASIA_SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final RealtimeLoadService realtimeLoadService;
    private final LoadDataService loadDataService;
    private final AlertEventService alertEventService;

    public GetStatsTool(RealtimeLoadService realtimeLoadService,
                        LoadDataService loadDataService,
                        AlertEventService alertEventService) {
        this.realtimeLoadService = realtimeLoadService;
        this.loadDataService = loadDataService;
        this.alertEventService = alertEventService;
    }

    @Override
    public String name() {
        return "get_stats";
    }

    @Override
    public java.util.Set<String> allowedRoles() {
        return java.util.Set.of("DISPATCHER", "OPERATOR", "SYSTEM_ADMIN");
    }

    @Override
    public String description() {
        return "获取当前实时负荷数据、统计指标（峰值/谷值/平均值/负荷率）和最近告警。" +
               "不传时间参数时仅返回当前实时负荷；传时间参数时查询该区间的统计信息。" +
               "注意：所有数据均为模拟（MOCK）数据，仅供演示和开发调试，不是真实电网 SCADA 数据。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> startTime = new LinkedHashMap<>();
        startTime.put("type", "string");
        startTime.put("description", "可选，统计开始时间（ISO 8601）");
        props.put("startTime", startTime);

        Map<String, Object> endTime = new LinkedHashMap<>();
        endTime.put("type", "string");
        endTime.put("description", "可选，统计结束时间（ISO 8601）");
        props.put("endTime", endTime);

        Map<String, Object> includeRealtime = new LinkedHashMap<>();
        includeRealtime.put("type", "boolean");
        includeRealtime.put("description", "是否包含当前实时负荷，默认 true");
        props.put("includeRealtime", includeRealtime);

        Map<String, Object> includeAlerts = new LinkedHashMap<>();
        includeAlerts.put("type", "boolean");
        includeAlerts.put("description", "是否返回最近告警，默认 false");
        props.put("includeAlerts", includeAlerts);

        schema.put("properties", props);
        schema.put("required", Collections.emptyList());
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

        boolean includeRealtime = !args.has("includeRealtime") || args.get("includeRealtime").asBoolean(true);
        boolean includeAlerts = args.has("includeAlerts") && args.get("includeAlerts").asBoolean(false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "MOCK");

        // 实时负荷
        if (includeRealtime) {
            RealtimeLoadPoint latest = realtimeLoadService.getLatest();
            if (latest != null) {
                result.put("currentLoad", latest.getLoadMw());
                result.put("temperature", latest.getTemperature());
                result.put("humidity", latest.getHumidity());
                result.put("timestamp", latest.getTimestamp());
                result.put("dataSource", latest.getSource() != null ? latest.getSource() : "MOCK");
            } else {
                result.put("currentLoad", null);
                result.put("dataSource", "MOCK");
                result.put("note", "实时数据尚未初始化");
            }
        }

        // 统计（有时间参数时）
        boolean hasTimeRange = args.has("startTime") && args.has("endTime");
        if (hasTimeRange) {
            try {
                String startStr = args.get("startTime").asText();
                String endStr = args.get("endTime").asText();
                LocalDateTime start = parseToShanghai(startStr);
                LocalDateTime end = parseToShanghai(endStr);

                if (start.isBefore(end)) {
                    LoadStats stats = loadDataService.getStats(start, end);
                    Map<String, Object> statsMap = new LinkedHashMap<>();
                    if (stats.getDataPoints() != null && stats.getDataPoints() > 0) {
                        statsMap.put("dataPoints", stats.getDataPoints());
                        statsMap.put("peakLoad", stats.getPeakLoad());
                        statsMap.put("peakTime", stats.getPeakTime() != null ? stats.getPeakTime().toString() : null);
                        statsMap.put("valleyLoad", stats.getValleyLoad());
                        statsMap.put("valleyTime", stats.getValleyTime() != null ? stats.getValleyTime().toString() : null);
                        statsMap.put("avgLoad", stats.getAvgLoad());
                        statsMap.put("loadRate", stats.getLoadRate());
                        statsMap.put("stdDeviation", stats.getStdDeviation());
                    } else {
                        statsMap.put("dataPoints", 0);
                        statsMap.put("note", "指定时间范围内无数据");
                    }
                    result.put("stats", statsMap);
                } else {
                    result.put("stats", Map.of("error", "startTime 必须早于 endTime"));
                }
            } catch (DateTimeParseException e) {
                result.put("stats", Map.of("error", "时间格式错误: " + e.getMessage()));
            }
        }

        // 最近告警
        if (includeAlerts) {
            try {
                var page = alertEventService.query(1, 5, null, null, null, true);
                List<Map<String, Object>> alerts = new ArrayList<>();
                for (AlertEvent a : page.getRecords()) {
                    Map<String, Object> alert = new LinkedHashMap<>();
                    alert.put("id", a.getId());
                    alert.put("level", a.getLevel());
                    alert.put("title", a.getAiAnalysis() != null ? a.getAiAnalysis() : a.getLevel());
                    alert.put("triggerTime", a.getTriggerTime() != null ? a.getTriggerTime().toString() : null);
                    alert.put("loadMw", a.getCurrentValue());
                    alerts.add(alert);
                }
                result.put("recentAlerts", alerts);
                result.put("unreadAlertCount", alerts.size());
            } catch (Exception e) {
                log.warn("查询告警失败", e);
                result.put("recentAlerts", Collections.emptyList());
                result.put("unreadAlertCount", 0);
            }
        } else {
            // 只返回未读数量
            try {
                var page = alertEventService.query(1, 5, null, null, null, true);
                result.put("unreadAlertCount", page.getRecords().size());
            } catch (Exception e) {
                result.put("unreadAlertCount", 0);
            }
            result.put("recentAlerts", Collections.emptyList());
        }

        // 摘要消息
        StringBuilder sb = new StringBuilder("当前系统状态（模拟数据）：");
        if (result.containsKey("currentLoad")) {
            sb.append(" 实时负荷 ").append(formatLoad(result.get("currentLoad"))).append(" MW。");
        }
        if (result.containsKey("stats")) {
            Object statsObj = result.get("stats");
            if (statsObj instanceof Map<?, ?> statsMap && statsMap.containsKey("peakLoad")) {
                sb.append(" 峰值 ").append(formatLoad(statsMap.get("peakLoad")))
                  .append(" MW，均值 ").append(formatLoad(statsMap.get("avgLoad"))).append(" MW。");
            }
        }
        sb.append(" 注意：所有数据为 MOCK 模拟数据，非真实电网数据。");

        return ToolResult.ok(sb.toString(), result);
    }

    private LocalDateTime parseToShanghai(String timeStr) {
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(timeStr, ISO_FMT);
            return zdt.withZoneSameInstant(ASIA_SHANGHAI).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    private static String formatLoad(Object val) {
        if (val instanceof Float f) return String.format("%.1f", f);
        if (val instanceof Double d) return String.format("%.1f", d);
        return String.valueOf(val);
    }
}
