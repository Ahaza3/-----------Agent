package com.powerload.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import com.powerload.dto.response.GridRiskSnapshot;
import com.powerload.service.GridTopologyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询拓扑节点风险工具。
 */
@Component
@RequiredArgsConstructor
public class QueryGridRiskTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GridTopologyService gridTopologyService;

    @Override
    public String name() {
        return "query_grid_risk";
    }

    @Override
    public String description() {
        return "查询区域、变电站和馈线的当前负荷、预测峰值、容量余量和风险等级。"
                + "适用于询问哪个节点风险最高、哪些节点即将过载、拓扑风险排名等。"
                + "当前数据为拓扑派生模拟结果，不能替代真实 SCADA 或潮流分析。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("riskLevel", Map.of(
                "type", "string",
                "enum", List.of("RED", "ORANGE", "YELLOW", "NORMAL"),
                "description", "可选，按风险等级筛选"));
        properties.put("nodeType", Map.of(
                "type", "string",
                "enum", List.of("REGION", "SUBSTATION", "FEEDER"),
                "description", "可选，按节点类型筛选"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public ToolResult execute(String argumentsJson) {
        try {
            JsonNode args = MAPPER.readTree(argumentsJson == null || argumentsJson.isBlank()
                    ? "{}" : argumentsJson);
            String riskLevel = textArg(args, "riskLevel");
            String nodeType = textArg(args, "nodeType");

            List<GridRiskSnapshot> snapshots = gridTopologyService.getRiskSnapshot().stream()
                    .filter(snapshot -> riskLevel == null || riskLevel.equals(snapshot.getRiskLevel()))
                    .filter(snapshot -> nodeType == null || nodeType.equals(snapshot.getNodeType()))
                    .toList();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("source", "DERIVED_SIMULATION");
            data.put("simulated", true);
            data.put("riskLevelFilter", riskLevel);
            data.put("nodeTypeFilter", nodeType);
            data.put("nodes", snapshots);
            data.put("alertRoots", snapshots.stream()
                    .filter(snapshot -> snapshot.getAlertRootNodeCode() != null)
                    .filter(snapshot -> snapshot.getNodeCode().equals(snapshot.getAlertRootNodeCode()))
                    .toList());
            data.put("deduplicatedNodes", snapshots.stream()
                    .filter(GridRiskSnapshot::isAlertDeduplicated)
                    .map(GridRiskSnapshot::getNodeCode)
                    .toList());
            data.put("ruleBasedActions", List.of(
                    "优先核查风险根节点及其下游馈线",
                    "对预测峰值超过容量的节点提前评估削峰或转供",
                    "发生节点或线路故障时先执行 query_grid_scenario 评估影响范围"));

            String message = snapshots.isEmpty()
                    ? "没有符合条件的拓扑节点风险记录。"
                    : "已返回 " + snapshots.size() + " 个拓扑节点风险记录，结果来自拓扑派生模拟数据。";
            ToolResult result = ToolResult.ok(message, data);
            result.setProvenance(Map.of(
                    "source", "DERIVED_SIMULATION",
                    "simulated", true,
                    "scope", "grid_topology"));
            return result;
        } catch (Exception e) {
            return ToolResult.fail("拓扑风险查询参数无效: " + e.getMessage());
        }
    }

    private String textArg(JsonNode args, String field) {
        JsonNode value = args != null ? args.get(field) : null;
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }
}
