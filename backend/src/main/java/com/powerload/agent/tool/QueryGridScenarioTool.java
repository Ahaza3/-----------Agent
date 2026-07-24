package com.powerload.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import com.powerload.dto.request.GridScenarioRequest;
import com.powerload.entity.GridEdge;
import com.powerload.entity.GridNode;
import com.powerload.mapper.GridEdgeMapper;
import com.powerload.mapper.GridNodeMapper;
import com.powerload.service.GridTopologyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询拓扑故障场景推演结果。
 */
@Component
@RequiredArgsConstructor
public class QueryGridScenarioTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GridTopologyService gridTopologyService;
    private final GridNodeMapper gridNodeMapper;
    private final GridEdgeMapper gridEdgeMapper;

    @Override
    public String name() {
        return "query_grid_scenario";
    }

    @Override
    public java.util.Set<String> allowedRoles() {
        return java.util.Set.of("DISPATCHER", "OPERATOR", "SYSTEM_ADMIN");
    }

    @Override
    public String description() {
        return "执行节点或线路故障后的拓扑影响场景推演，返回受影响节点、受影响负荷、可转供余量和未供负荷。"
                + "计算由拓扑规则完成，适用于询问节点故障、线路故障、影响范围、风险根因和处置建议。"
                + "当前结果为模拟拓扑推演，不代表真实潮流或 SCADA 结果。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("targetType", Map.of(
                "type", "string",
                "enum", List.of("NODE", "EDGE"),
                "description", "故障目标类型"));
        properties.put("nodeId", Map.of(
                "type", "integer",
                "description", "NODE 场景可选，拓扑节点 ID"));
        properties.put("nodeCode", Map.of(
                "type", "string",
                "description", "NODE 场景可选，拓扑节点编码，例如 FEEDER-E-01"));
        properties.put("edgeId", Map.of(
                "type", "integer",
                "description", "EDGE 场景可选，拓扑线路 ID"));
        properties.put("fromNodeCode", Map.of(
                "type", "string",
                "description", "EDGE 场景可选，线路起点节点编码"));
        properties.put("toNodeCode", Map.of(
                "type", "string",
                "description", "EDGE 场景可选，线路终点节点编码"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("targetType"));
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public ToolResult execute(String argumentsJson) {
        try {
            JsonNode args = MAPPER.readTree(argumentsJson == null || argumentsJson.isBlank()
                    ? "{}" : argumentsJson);
            String targetType = textArg(args, "targetType");
            GridScenarioRequest request = new GridScenarioRequest();
            request.setTargetType(targetType);
            if ("NODE".equalsIgnoreCase(targetType)) {
                request.setNodeId(resolveNodeId(args));
            } else if ("EDGE".equalsIgnoreCase(targetType)) {
                request.setEdgeId(resolveEdgeId(args));
            }

            var scenario = gridTopologyService.simulateScenario(request);
            ToolResult result = ToolResult.ok("拓扑场景推演已完成，结果来自模拟拓扑规则计算。", scenario);
            result.setProvenance(Map.of(
                    "source", "TOPOLOGY_SCENARIO_SIMULATION",
                    "simulated", true,
                    "calculation", "rule_based_topology"));
            return result;
        } catch (Exception e) {
            return ToolResult.fail("拓扑场景推演失败: " + e.getMessage());
        }
    }

    private Long resolveNodeId(JsonNode args) {
        Long nodeId = longArg(args, "nodeId");
        if (nodeId != null) {
            return nodeId;
        }
        String nodeCode = textArg(args, "nodeCode");
        if (nodeCode == null) {
            throw new IllegalArgumentException("NODE 场景需要 nodeId 或 nodeCode");
        }
        GridNode node = gridNodeMapper.selectOne(new LambdaQueryWrapper<GridNode>()
                .eq(GridNode::getNodeCode, nodeCode)
                .last("LIMIT 1"));
        if (node == null) {
            throw new IllegalArgumentException("节点不存在: " + nodeCode);
        }
        return node.getId();
    }

    private Long resolveEdgeId(JsonNode args) {
        Long edgeId = longArg(args, "edgeId");
        if (edgeId != null) {
            return edgeId;
        }
        String fromCode = textArg(args, "fromNodeCode");
        String toCode = textArg(args, "toNodeCode");
        if (fromCode == null || toCode == null) {
            throw new IllegalArgumentException("EDGE 场景需要 edgeId，或 fromNodeCode + toNodeCode");
        }
        GridNode from = gridNodeMapper.selectOne(new LambdaQueryWrapper<GridNode>()
                .eq(GridNode::getNodeCode, fromCode)
                .last("LIMIT 1"));
        GridNode to = gridNodeMapper.selectOne(new LambdaQueryWrapper<GridNode>()
                .eq(GridNode::getNodeCode, toCode)
                .last("LIMIT 1"));
        if (from == null || to == null) {
            throw new IllegalArgumentException("线路节点不存在");
        }
        GridEdge edge = gridEdgeMapper.selectOne(new LambdaQueryWrapper<GridEdge>()
                .eq(GridEdge::getFromNodeId, from.getId())
                .eq(GridEdge::getToNodeId, to.getId())
                .last("LIMIT 1"));
        if (edge == null) {
            throw new IllegalArgumentException("线路不存在: " + fromCode + " -> " + toCode);
        }
        return edge.getId();
    }

    private String textArg(JsonNode args, String field) {
        JsonNode value = args != null ? args.get(field) : null;
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    private Long longArg(JsonNode args, String field) {
        JsonNode value = args != null ? args.get(field) : null;
        if (value == null || value.isNull() || !value.canConvertToLong()) {
            return null;
        }
        return value.asLong();
    }
}
