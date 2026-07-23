package com.powerload.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import com.powerload.entity.*;
import com.powerload.service.TicketService;
import com.powerload.service.TicketFeedbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class QueryAlertTicketTool implements Tool {

    private final TicketService ticketService;
    private final TicketFeedbackService ticketFeedbackService;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public QueryAlertTicketTool(TicketService ticketService,
                                TicketFeedbackService ticketFeedbackService) {
        this.ticketService = ticketService;
        this.ticketFeedbackService = ticketFeedbackService;
    }

    @Override public String name() { return "query_alert_ticket"; }

    @Override public java.util.Set<String> allowedRoles() {
        return java.util.Set.of("DISPATCHER", "OPERATOR", "SYSTEM_ADMIN");
    }

    @Override public String description() {
        return "查询告警处置工单信息：当前状态、优先级、处理人、处置时间线、处理结果等。"
             + "可按工单编号(ticketNo)、工单ID(ticketId)或告警ID(alertId)查询。"
             + "仅供只读查询，不可通过此工具修改工单状态。";
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        var p = new LinkedHashMap<String, Object>();
        p.put("ticketId", Map.of("type", "integer", "description", "工单ID"));
        p.put("ticketNo", Map.of("type", "string", "description", "工单编号"));
        p.put("alertId", Map.of("type", "integer", "description", "告警ID"));
        s.put("properties", p);
        return s;
    }

    @Override
    public ToolResult execute(String args) {
        try {
            var node = MAPPER.readTree(args);
            AlertTicket ticket = null;
            if (node.has("ticketId")) ticket = ticketService.getById(node.get("ticketId").asLong());
            else if (node.has("ticketNo")) ticket = ticketService.getByTicketNo(node.get("ticketNo").asText());
            else if (node.has("alertId")) ticket = ticketService.getByAlertId(node.get("alertId").asLong());

            if (ticket == null) return ToolResult.fail("未找到工单，请提供有效的 ticketId/ticketNo/alertId。");

            List<AlertTicketAction> actions = ticketService.getActions(ticket.getId());
            List<Map<String, Object>> timeline = new ArrayList<>();
            for (AlertTicketAction a : actions) {
                Map<String, Object> step = new LinkedHashMap<>();
                step.put("action", a.getAction()); step.put("operator", a.getOperatorName());
                step.put("role", a.getOperatorRole()); step.put("note", a.getNote());
                step.put("fromStatus", a.getFromStatus()); step.put("toStatus", a.getToStatus());
                step.put("time", a.getCreatedAt() != null ? a.getCreatedAt().toString() : "");
                timeline.add(step);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ticketNo", ticket.getTicketNo());
            data.put("status", ticket.getStatus()); data.put("priority", ticket.getPriority());
            data.put("summary", ticket.getSummary());
            data.put("assignee", ticket.getAssigneeName() != null ? ticket.getAssigneeName() : "未分配");
            data.put("createdBy", ticket.getCreatedByName());
            data.put("resolution", ticket.getResolution()); data.put("timeline", timeline);
            Map<String, Object> feedback = ticketFeedbackService.readOnlySummary(ticket.getId());
            feedback.remove("alertEvidence");
            feedback.remove("rootCauseDetail");
            data.put("feedback", feedback);
            data.put("source", "MOCK_SYSTEM");

            String desc = String.format("工单 %s 当前状态: %s, 优先级: %s, 处理人: %s",
                    ticket.getTicketNo(), ticket.getStatus(), ticket.getPriority(),
                    ticket.getAssigneeName() != null ? ticket.getAssigneeName() : "未分配");
            return ToolResult.ok(desc, data);
        } catch (Exception e) {
            return ToolResult.fail("工单查询失败: " + e.getMessage());
        }
    }
}
