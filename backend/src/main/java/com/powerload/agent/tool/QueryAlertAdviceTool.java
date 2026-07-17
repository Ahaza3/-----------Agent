package com.powerload.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import com.powerload.entity.AlertAdvice;
import com.powerload.mapper.AlertAdviceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 告警建议查询工具 — 返回指定告警的角色化建议（调度员/运维管理员）。
 */
@Slf4j
@Component
public class QueryAlertAdviceTool implements Tool {

    private final AlertAdviceMapper adviceMapper;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public QueryAlertAdviceTool(AlertAdviceMapper adviceMapper) {
        this.adviceMapper = adviceMapper;
    }

    @Override public String name() { return "query_alert_advice"; }

    @Override public String description() {
        return "查询某条告警的 AI 建议，包含分析结论和措施列表。可按角色(DISPATCHER/OPERATOR)筛选。";
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        var props = new LinkedHashMap<String, Object>();
        props.put("alertId", Map.of("type", "integer", "description", "告警事件ID"));
        props.put("role", Map.of("type", "string", "description", "可选，筛选角色: DISPATCHER/OPERATOR"));
        s.put("properties", props);
        s.put("required", List.of("alertId"));
        return s;
    }

    @Override
    public ToolResult execute(String args) {
        try {
            var node = MAPPER.readTree(args);
            long alertId = node.get("alertId").asLong();
            String role = node.has("role") ? node.get("role").asText() : null;

            var wrapper = new LambdaQueryWrapper<AlertAdvice>().eq(AlertAdvice::getAlertId, alertId);
            if (role != null) wrapper.eq(AlertAdvice::getAudienceRole, role);
            List<AlertAdvice> advices = adviceMapper.selectList(wrapper);

            if (advices.isEmpty()) return ToolResult.fail("该告警暂无 AI 建议");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("source", "MOCK_AI");
            List<Map<String, Object>> list = new ArrayList<>();
            for (AlertAdvice a : advices) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("role", a.getAudienceRole());
                item.put("status", a.getStatus());
                item.put("analysis", a.getAnalysis());
                item.put("model", a.getModelName());
                list.add(item);
            }
            data.put("advices", list);
            return ToolResult.ok("告警建议 (可能有多个角色): " + list.size() + " 条", data);
        } catch (Exception ex) {
            return ToolResult.fail("建议查询失败: " + ex.getMessage());
        }
    }
}
