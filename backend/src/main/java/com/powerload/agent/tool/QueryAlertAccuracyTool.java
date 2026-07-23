package com.powerload.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import com.powerload.entity.AlertEvent;
import com.powerload.entity.AlertTicket;
import com.powerload.entity.TicketFeedback;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.mapper.AlertTicketMapper;
import com.powerload.mapper.TicketFeedbackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 工具：查询告警准确率与误报分析
 *
 * <p>用于分析指定节点或时间段内的告警准确性，识别误报模式并生成优化建议。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryAlertAccuracyTool implements Tool {

    private final AlertTicketMapper ticketMapper;
    private final TicketFeedbackMapper feedbackMapper;
    private final AlertEventMapper alertEventMapper;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "query_alert_accuracy";
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("DISPATCHER", "OPERATOR", "SYSTEM_ADMIN");
    }

    @Override
    public String description() {
        return "分析告警准确率与误报模式，按节点或时间段统计真实告警、误报数量、准确率和主要误报根因。"
             + "用于识别规则调优机会，帮助减少误报。"
             + "参数：nodeId(可选，拓扑节点ID)、days(可选，统计天数，默认30天)、level(可选，告警级别筛选)。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("nodeId", Map.of(
            "type", "integer",
            "description", "拓扑节点ID，用于分析特定节点的告警准确性"
        ));
        props.put("days", Map.of(
            "type", "integer",
            "description", "统计时间范围（天），默认30天，最大90天"
        ));
        props.put("level", Map.of(
            "type", "string",
            "description", "告警级别筛选：RED/ORANGE/YELLOW"
        ));
        schema.put("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(String args) {
        try {
            var node = MAPPER.readTree(args);
            Long nodeId = node.has("nodeId") && !node.get("nodeId").isNull()
                    ? node.get("nodeId").asLong() : null;
            int days = node.has("days") ? Math.min(90, Math.max(1, node.get("days").asInt(30))) : 30;
            String level = node.has("level") && !node.get("level").isNull()
                    ? node.get("level").asText() : null;

            LocalDateTime cutoff = LocalDateTime.now().minusDays(days);

            // 查询时间窗口内的已关闭工单
            var ticketQuery = new LambdaQueryWrapper<AlertTicket>()
                    .eq(AlertTicket::getStatus, "CLOSED")
                    .ge(AlertTicket::getCreatedAt, cutoff);
            List<AlertTicket> tickets = ticketMapper.selectList(ticketQuery);

            // 加载反馈
            Set<Long> ticketIds = tickets.stream().map(AlertTicket::getId).collect(Collectors.toSet());
            Map<Long, TicketFeedback> feedbackMap = ticketIds.isEmpty() ? Map.of()
                    : feedbackMapper.selectList(new LambdaQueryWrapper<TicketFeedback>()
                            .in(TicketFeedback::getTicketId, ticketIds))
                      .stream().collect(Collectors.toMap(TicketFeedback::getTicketId, f -> f));

            // 加载告警事件（用于节点和级别筛选）
            Set<Long> alertIds = tickets.stream().map(AlertTicket::getAlertId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, AlertEvent> alertMap = alertIds.isEmpty() ? Map.of()
                    : alertEventMapper.selectBatchIds(alertIds).stream()
                      .collect(Collectors.toMap(AlertEvent::getId, a -> a));

            // 统计
            int totalClosedTickets = 0;
            int withFeedback = 0;
            int trueAlertCount = 0;
            int falsePositiveCount = 0;
            int duplicateCount = 0;
            int inconclusiveCount = 0;
            Map<String, Integer> rootCauses = new HashMap<>();

            for (AlertTicket ticket : tickets) {
                AlertEvent alert = ticket.getAlertId() != null ? alertMap.get(ticket.getAlertId()) : null;

                // 节点筛选
                if (nodeId != null && (alert == null || !nodeId.equals(alert.getNodeId()))) {
                    continue;
                }
                // 级别筛选
                if (level != null && (alert == null || !level.equals(alert.getLevel()))) {
                    continue;
                }

                totalClosedTickets++;
                TicketFeedback feedback = feedbackMap.get(ticket.getId());
                if (feedback == null) continue;

                withFeedback++;
                String classification = feedback.getAlertClassification();
                if ("TRUE_ALERT".equals(classification)) trueAlertCount++;
                else if ("FALSE_POSITIVE".equals(classification)) {
                    falsePositiveCount++;
                    String rootCause = feedback.getRootCauseCode();
                    if (rootCause != null && !rootCause.isBlank()) {
                        rootCauses.merge(rootCause, 1, Integer::sum);
                    }
                } else if ("DUPLICATE".equals(classification)) duplicateCount++;
                else if ("INCONCLUSIVE".equals(classification)) inconclusiveCount++;
            }

            // 计算准确率
            int validSamples = trueAlertCount + falsePositiveCount;
            BigDecimal accuracy = validSamples > 0
                    ? BigDecimal.valueOf(trueAlertCount).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(validSamples), 1, RoundingMode.HALF_UP)
                    : null;

            // 构造结果
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("timeRange", days + "天");
            result.put("cutoffDate", cutoff.toLocalDate().toString());
            if (nodeId != null) result.put("nodeId", nodeId);
            if (level != null) result.put("level", level);
            result.put("totalClosedTickets", totalClosedTickets);
            result.put("withFeedback", withFeedback);
            result.put("trueAlertCount", trueAlertCount);
            result.put("falsePositiveCount", falsePositiveCount);
            result.put("duplicateCount", duplicateCount);
            result.put("inconclusiveCount", inconclusiveCount);
            result.put("accuracyPercent", accuracy);

            // 误报根因排序（按频次降序）
            List<Map<String, Object>> topRootCauses = rootCauses.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .map(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("rootCause", e.getKey());
                        item.put("count", e.getValue());
                        return item;
                    })
                    .toList();
            result.put("topFalsePositiveRootCauses", topRootCauses);

            // 生成建议
            List<String> suggestions = new ArrayList<>();
            if (falsePositiveCount > trueAlertCount && falsePositiveCount > 5) {
                suggestions.add("误报数量超过真实告警，建议重点核查告警规则配置");
            }
            if (rootCauses.containsKey("THRESHOLD_TOO_LOW") && rootCauses.get("THRESHOLD_TOO_LOW") >= 3) {
                suggestions.add("多次因'阈值过低'误报，建议分析历史负荷峰值并适当上调阈值");
            }
            if (rootCauses.containsKey("DATA_ANOMALY") && rootCauses.get("DATA_ANOMALY") >= 3) {
                suggestions.add("多次因'数据异常'误报，建议核查遥测数据质量或增加数据校验逻辑");
            }
            if (duplicateCount > 5) {
                suggestions.add("存在较多重复告警工单，建议优化告警去重或合并逻辑");
            }
            result.put("suggestions", suggestions);

            String summary = String.format("统计周期 %d 天，已关闭工单 %d 条，有反馈 %d 条。真实告警 %d 条，误报 %d 条，准确率 %s%%。",
                    days, totalClosedTickets, withFeedback, trueAlertCount, falsePositiveCount,
                    accuracy != null ? accuracy : "N/A");

            return ToolResult.ok(summary, result);

        } catch (Exception e) {
            log.error("查询告警准确率失败", e);
            return ToolResult.fail("查询告警准确率时发生错误：" + e.getMessage());
        }
    }
}
