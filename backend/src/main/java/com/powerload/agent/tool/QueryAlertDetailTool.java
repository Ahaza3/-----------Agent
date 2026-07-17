package com.powerload.agent.tool;

import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import com.powerload.entity.AlertEvent;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.service.AlertEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 告警详情查询工具 — 返回指定告警的完整证据。
 *
 * <p>数据来源 MOCK_ALERT，模拟数据仅供演示。</p>
 */
@Slf4j
@Component
public class QueryAlertDetailTool implements Tool {

    private final AlertEventMapper alertEventMapper;

    public QueryAlertDetailTool(AlertEventMapper alertEventMapper) {
        this.alertEventMapper = alertEventMapper;
    }

    @Override public String name() { return "query_alert_detail"; }

    @Override public String description() {
        return "查询某条告警事件的完整详情：告警级别、类型、当前负荷、阈值、触发时间、超限比例等。";
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", Map.of("alertId", Map.of("type", "integer", "description", "告警事件ID")));
        s.put("required", List.of("alertId"));
        return s;
    }

    @Override
    public ToolResult execute(String args) {
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(args);
            long alertId = node.get("alertId").asLong();
            AlertEvent e = alertEventMapper.selectById(alertId);
            if (e == null) return ToolResult.fail("告警 ID=" + alertId + " 不存在");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("source", "MOCK_ALERT");
            data.put("level", e.getLevel());
            data.put("type", e.getType());
            data.put("currentLoad", e.getCurrentValue());
            data.put("threshold", e.getThresholdValue());
            data.put("triggerTime", e.getTriggerTime() != null ? e.getTriggerTime().toString() : null);
            if (e.getThresholdValue() != null && e.getThresholdValue() > 0) {
                float ratio = (e.getCurrentValue() - e.getThresholdValue()) / e.getThresholdValue() * 100;
                data.put("overRatio", String.format("%.1f%%", ratio));
            }
            data.put("aiAnalysis", e.getAiAnalysis());
            data.put("suggestion", e.getSuggestion());
            return ToolResult.ok("告警详情 (模拟数据): " + data.get("level"), data);
        } catch (Exception ex) {
            return ToolResult.fail("告警查询失败: " + ex.getMessage());
        }
    }
}
