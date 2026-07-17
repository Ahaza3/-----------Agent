package com.powerload.agent.tool;

import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import com.powerload.service.SystemHealthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class QuerySystemHealthTool implements Tool {

    private final SystemHealthService healthService;

    public QuerySystemHealthTool(SystemHealthService healthService) {
        this.healthService = healthService;
    }

    @Override public String name() { return "query_system_health"; }

    @Override public String description() {
        return "查询系统健康状态：MySQL、Flask/LSTM、LLM配置、最近预测和告警时间等。";
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", Map.of());
        return s;
    }

    @Override
    public ToolResult execute(String args) {
        try {
            Map<String, Object> health = healthService.check();
            health.put("source", "SYSTEM_HEALTH");
            return ToolResult.ok("系统健康检查完成", health);
        } catch (Exception e) {
            return ToolResult.fail("健康检查失败: " + e.getMessage());
        }
    }
}
