package com.powerload.agent.tool;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.powerload.alert.AlertJudgementService;
import com.powerload.dto.response.AlertJudgementResult;
import com.powerload.entity.AlertEvent;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询告警智能研判 — 告诉 Agent 某个告警是否应该建单、风险等级、调度/运维建议。
 */
@Component
@RequiredArgsConstructor
public class QueryAlertJudgementTool implements Tool {

    private final AlertJudgementService alertJudgementService;
    private final AlertEventMapper alertEventMapper;

    @Override
    public String name() { return "query_alert_judgement"; }

    @Override
    public java.util.Set<String> allowedRoles() {
        return java.util.Set.of("DISPATCHER", "SYSTEM_ADMIN");
    }

    @Override
    public String description() {
        return "查询最近一条告警的智能研判结果，或按 alertId 查询指定告警的研判。" +
               "返回：是否建议提交待确认工单草稿、推荐优先级、调度员建议、运维建议、研判原因。" +
               "调度员询问'这个告警要不要建单/怎么处理/是否需要升级'时必须调用此工具。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("alertId", Map.of("type", "integer", "description", "告警ID，不传则查询最近一条RED/ORANGE/YELLOW告警"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(String argumentsJson) {
        try {
            JSONObject args = JSONUtil.parseObj(argumentsJson);
            Long alertId = args.getLong("alertId");

            AlertEvent event;
            if (alertId != null) {
                event = alertEventMapper.selectById(alertId);
                if (event == null) return ToolResult.fail("告警 " + alertId + " 不存在");
            } else {
                // 取最近一条未关闭告警
                var events = alertEventMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AlertEvent>()
                                .in(AlertEvent::getLevel, List.of("RED", "ORANGE", "YELLOW"))
                                .orderByDesc(AlertEvent::getTriggerTime)
                                .last("LIMIT 1"));
                if (events.isEmpty()) return ToolResult.fail("当前无活跃告警");
                event = events.get(0);
            }

            AlertJudgementResult j = alertJudgementService.judge(event);

            StringBuilder sb = new StringBuilder();
            sb.append("【智能研判】告警 #").append(event.getId()).append("\n");
            sb.append("告警级别：").append(event.getLevel()).append("\n");
            sb.append("当前负荷：").append(String.format("%.1f MW", event.getCurrentValue())).append("\n");
            sb.append("阈值：").append(String.format("%.0f MW", event.getThresholdValue())).append("\n");
            sb.append("趋势：").append(j.getTrendDirection()).append("\n");
            if (j.getForecastPeakLoad() != null) {
                sb.append("预测峰值：").append(String.format("%.0f MW", j.getForecastPeakLoad())).append("\n");
            }
            sb.append("是否建议建单：").append(Boolean.TRUE.equals(j.getShouldCreateTicket()) ? "是" : "否").append("\n");
            sb.append("待确认工单草稿：").append(Boolean.TRUE.equals(j.getShouldCreateTicket()) ? "建议提交" : "暂不建议提交").append("\n");
            sb.append("推荐优先级：").append(j.getRecommendedPriority()).append("\n\n");
            sb.append("研判原因：").append(j.getDecisionReason()).append("\n\n");
            sb.append("调度员建议：").append(j.getDispatcherAdvice()).append("\n");
            sb.append("运维建议：").append(j.getOperatorAdvice()).append("\n");
            sb.append("\n（来源：规则型智能 Agent，仅供人工决策参考）");

            return ToolResult.ok(name(), sb.toString());
        } catch (Exception e) {
            return ToolResult.fail("查询研判失败: " + e.getMessage());
        }
    }
}
