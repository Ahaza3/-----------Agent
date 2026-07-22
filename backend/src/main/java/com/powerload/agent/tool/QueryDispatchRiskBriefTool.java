package com.powerload.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import com.powerload.common.GridTopologyConstants;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.AlertEvent;
import com.powerload.entity.AlertRule;
import com.powerload.entity.AlertTicket;
import com.powerload.entity.PredictionResult;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.mapper.AlertRuleMapper;
import com.powerload.mapper.AlertTicketMapper;
import com.powerload.mapper.PredictionResultMapper;
import com.powerload.service.RealtimeLoadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class QueryDispatchRiskBriefTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RealtimeLoadService realtimeLoadService;
    private final PredictionResultMapper predictionResultMapper;
    private final AlertRuleMapper alertRuleMapper;
    private final AlertEventMapper alertEventMapper;
    private final AlertTicketMapper ticketMapper;

    @Override
    public String name() {
        return "query_dispatch_risk_brief";
    }

    @Override
    public java.util.Set<String> allowedRoles() {
        return java.util.Set.of("DISPATCHER", "SYSTEM_ADMIN");
    }

    @Override
    public String description() {
        return "生成调度员当前运行风险简报，聚合实时负荷、预测峰值、告警阈值、未读告警和预警工单建议。"
                + "适用于询问运行风险、未来峰值、是否需要提前建预警工单、调度建议等。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        return schema;
    }

    @Override
    public ToolResult execute(String args) {
        RealtimeLoadPoint latest = realtimeLoadService.getLatest();
        Thresholds thresholds = loadThresholds();
        List<PredictionResult> forecast = latestForecastBatch();
        PredictionResult peak = forecast.stream()
                .max(Comparator.comparing(PredictionResult::getPredictedLoad,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);

        float peakLoad = peak != null && peak.getPredictedLoad() != null ? peak.getPredictedLoad() : 0f;
        String riskLevel = riskLevel(peakLoad, thresholds);

        long unreadAlerts = alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getIsRead, 0));
        long openTickets = ticketMapper.selectCount(new LambdaQueryWrapper<AlertTicket>()
                .notIn(AlertTicket::getStatus, List.of("CLOSED", "CANCELLED")));
        long prewarningTickets = ticketMapper.selectCount(new LambdaQueryWrapper<AlertTicket>()
                .eq(AlertTicket::getSourceType, "PREWARNING")
                .notIn(AlertTicket::getStatus, List.of("CLOSED", "CANCELLED")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("source", "MOCK_OPERATION_BRIEF");
        data.put("currentLoadMw", latest != null ? latest.getLoadMw() : null);
        data.put("currentTime", latest != null ? LocalDateTime.now().toString() : null);
        data.put("thresholds", Map.of(
                "yellow", thresholds.yellow,
                "orange", thresholds.orange,
                "red", thresholds.red));
        data.put("forecastPeakLoadMw", peakLoad > 0 ? peakLoad : null);
        data.put("forecastPeakTime", peak != null && peak.getPredictTime() != null
                ? peak.getPredictTime().toString() : null);
        data.put("forecastRiskLevel", riskLevel);
        data.put("unreadAlertCount", unreadAlerts);
        data.put("openTicketCount", openTickets);
        data.put("openPrewarningTicketCount", prewarningTickets);
        data.put("prewarningTicketRecommended", peak != null && !"NORMAL".equals(riskLevel));
        data.put("recommendedActions", actionsFor(riskLevel));

        String message = "调度风险简报已生成：预测风险=" + riskLevel
                + (peak != null ? "，峰值约 " + String.format("%.1f MW", peakLoad) : "，暂无预测峰值")
                + "。数据为模拟环境结果，仅供人工决策参考。";
        return ToolResult.ok(message, data);
    }

    private List<PredictionResult> latestForecastBatch() {
        PredictionResult latest = predictionResultMapper.selectOne(new LambdaQueryWrapper<PredictionResult>()
                .eq(PredictionResult::getNodeId, GridTopologyConstants.ROOT_NODE_ID)
                .orderByDesc(PredictionResult::getCreatedAt)
                .last("LIMIT 1"));
        if (latest == null || latest.getCreatedAt() == null) return List.of();
        return predictionResultMapper.selectList(new LambdaQueryWrapper<PredictionResult>()
                .eq(PredictionResult::getNodeId, GridTopologyConstants.ROOT_NODE_ID)
                .eq(PredictionResult::getCreatedAt, latest.getCreatedAt())
                .orderByAsc(PredictionResult::getPredictTime));
    }

    private Thresholds loadThresholds() {
        AlertRule rule = alertRuleMapper.selectOne(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getIsActive, 1)
                .eq(AlertRule::getType, "THRESHOLD")
                .orderByDesc(AlertRule::getUpdatedAt)
                .last("LIMIT 1"));
        if (rule == null || rule.getConfig() == null) return new Thresholds(990f, 1100f, 1210f);
        try {
            var node = MAPPER.readTree(rule.getConfig());
            float base = (float) node.path("threshold").asDouble(1100);
            float yellow = base * (float) node.path("yellowRatio").asDouble(0.9);
            float orange = base * (float) node.path("orangeRatio").asDouble(1.0);
            float red = base * (float) node.path("redRatio").asDouble(1.1);
            return new Thresholds(yellow, orange, red);
        } catch (Exception ignored) {
            return new Thresholds(990f, 1100f, 1210f);
        }
    }

    private String riskLevel(float load, Thresholds thresholds) {
        if (load <= 0) return "UNKNOWN";
        if (load >= thresholds.red) return "RED";
        if (load >= thresholds.orange) return "ORANGE";
        if (load >= thresholds.yellow) return "YELLOW";
        return "NORMAL";
    }

    private List<String> actionsFor(String riskLevel) {
        return switch (riskLevel) {
            case "RED" -> List.of("提前通知运维准备处置资源", "评估是否创建预警工单", "关注峰值时段前后的实时爬升速度");
            case "ORANGE" -> List.of("复核预测峰值和阈值配置", "准备调峰预案", "视负荷上升趋势创建预警工单");
            case "YELLOW" -> List.of("保持监视并关注峰值时段", "检查是否存在未关闭告警工单", "峰前 30 分钟复核预测");
            default -> List.of("维持常规监视", "关注实时负荷与预测偏差", "无需提前建单");
        };
    }

    private record Thresholds(float yellow, float orange, float red) {}
}
