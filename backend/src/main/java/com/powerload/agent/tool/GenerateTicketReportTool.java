package com.powerload.agent.tool;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import com.powerload.ticket.TicketReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生成工单处理报告 — 运维人员询问"怎么处理/帮我生成报告"时调用。
 */
@Component
@RequiredArgsConstructor
public class GenerateTicketReportTool implements Tool {

    private final TicketReportService ticketReportService;

    @Override
    public String name() { return "generate_ticket_report"; }

    @Override
    public String description() {
        return "按 ticketId 生成结构化工单处理报告（规则型 Agent 模板，非 LLM）。" +
               "运维人员询问'这个工单怎么处理/帮我生成处理报告/处理结果怎么写'时必须调用此工具。" +
               "返回包含工单信息、问题研判、处理过程、处理结果、后续建议的结构化报告。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("ticketId", Map.of("type", "integer", "description", "工单ID"));
        props.put("operatorNote", Map.of("type", "string", "description", "运维人员填写的处理过程说明"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", java.util.List.of("ticketId"));
        return schema;
    }

    @Override
    public ToolResult execute(String argumentsJson) {
        try {
            JSONObject args = JSONUtil.parseObj(argumentsJson);
            Long ticketId = args.getLong("ticketId");
            if (ticketId == null) return ToolResult.fail("缺少 ticketId 参数");

            String operatorNote = args.getStr("operatorNote", "");
            Map<String, Object> report = ticketReportService.generateReport(ticketId, operatorNote);

            Object reportText = report.get("report");
            return ToolResult.ok(name(),
                    (reportText != null ? reportText.toString() : "报告生成失败") +
                    "\n\n（来源：规则型智能 Agent，非 LLM 生成）");
        } catch (Exception e) {
            return ToolResult.fail("生成报告失败: " + e.getMessage());
        }
    }
}
