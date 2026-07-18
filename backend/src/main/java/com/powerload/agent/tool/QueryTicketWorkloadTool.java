package com.powerload.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import com.powerload.agent.UserContextHolder;
import com.powerload.entity.AlertTicket;
import com.powerload.mapper.AlertTicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class QueryTicketWorkloadTool implements Tool {

    private final AlertTicketMapper ticketMapper;

    @Override
    public String name() {
        return "query_ticket_workload";
    }

    @Override
    public String description() {
        return "查询运维工单工作负载，包括待认领、分配给我、处理中、预警工单和最近工单。适用于运维排查、工单优先级和处理建议。";
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
        Long userId = UserContextHolder.currentUserId();

        long pending = ticketMapper.selectCount(new LambdaQueryWrapper<AlertTicket>()
                .eq(AlertTicket::getStatus, "PENDING"));
        long inProgressMine = userId == null ? 0 : ticketMapper.selectCount(new LambdaQueryWrapper<AlertTicket>()
                .eq(AlertTicket::getStatus, "IN_PROGRESS")
                .eq(AlertTicket::getAssigneeUserId, userId));
        long assignedMine = userId == null ? 0 : ticketMapper.selectCount(new LambdaQueryWrapper<AlertTicket>()
                .eq(AlertTicket::getStatus, "ASSIGNED")
                .eq(AlertTicket::getAssigneeUserId, userId));
        long prewarningOpen = ticketMapper.selectCount(new LambdaQueryWrapper<AlertTicket>()
                .eq(AlertTicket::getSourceType, "PREWARNING")
                .notIn(AlertTicket::getStatus, List.of("CLOSED", "CANCELLED")));

        List<AlertTicket> recent = ticketMapper.selectList(new LambdaQueryWrapper<AlertTicket>()
                .notIn(AlertTicket::getStatus, List.of("CLOSED", "CANCELLED"))
                .orderByDesc(AlertTicket::getCreatedAt)
                .last("LIMIT 8"));

        List<Map<String, Object>> recentItems = new ArrayList<>();
        for (AlertTicket ticket : recent) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ticketNo", ticket.getTicketNo());
            item.put("sourceType", ticket.getSourceType());
            item.put("priority", ticket.getPriority());
            item.put("status", ticket.getStatus());
            item.put("summary", ticket.getSummary());
            item.put("assigneeName", ticket.getAssigneeName());
            item.put("forecastTime", ticket.getForecastTime() != null ? ticket.getForecastTime().toString() : null);
            item.put("expectedLoad", ticket.getExpectedLoad());
            recentItems.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("source", "MOCK_TICKET_WORKLOAD");
        data.put("pendingCount", pending);
        data.put("assignedToMeCount", assignedMine);
        data.put("inProgressMineCount", inProgressMine);
        data.put("openPrewarningCount", prewarningOpen);
        data.put("recentOpenTickets", recentItems);
        data.put("recommendedActions", List.of(
                "优先处理 URGENT/HIGH 工单",
                "预警工单先核对预测峰值、阈值和实时爬升趋势",
                "处理前记录排查步骤，解决后填写处置结果"));

        return ToolResult.ok("工单负载已查询：待认领 " + pending + " 个，分配给我 "
                + assignedMine + " 个，处理中 " + inProgressMine + " 个。", data);
    }
}
