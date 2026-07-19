package com.powerload.ticket;

import com.powerload.entity.AlertEvent;
import com.powerload.entity.AlertTicket;
import com.powerload.entity.AlertTicketAction;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.mapper.AlertTicketActionMapper;
import com.powerload.mapper.AlertTicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 工单报告生成服务 — 纯模板引擎，不调用 LLM。
 * 生成结构化中文处理报告，适合答辩展示和运维记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketReportService {

    private final AlertTicketMapper ticketMapper;
    private final AlertTicketActionMapper actionMapper;
    private final AlertEventMapper alertEventMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 生成处理报告 */
    public Map<String, Object> generateReport(Long ticketId, String operatorNote) {
        AlertTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) throw new IllegalArgumentException("工单不存在: " + ticketId);

        AlertEvent alert = ticket.getAlertId() != null
                ? alertEventMapper.selectById(ticket.getAlertId())
                : null;

        String operatorText = (operatorNote == null || operatorNote.isBlank())
                ? "（待补充：运维人员尚未填写处理过程）"
                : operatorNote;

        StringBuilder report = new StringBuilder();

        // 一、工单基本信息
        report.append("【工单处理报告】\n\n");
        report.append("一、工单基本信息\n");
        report.append("工单编号：").append(ticket.getTicketNo()).append("\n");
        report.append("风险来源：").append(alert != null ? "告警触发" : "预测预警").append("\n");
        report.append("风险级别：").append(ticket.getPriority() != null ? ticket.getPriority() : "N/A").append("\n");
        report.append("创建时间：").append(ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(FMT) : "N/A").append("\n");
        if (alert != null) {
            report.append("触发时间：").append(alert.getTriggerTime() != null ? alert.getTriggerTime().format(FMT) : "N/A").append("\n");
            report.append("负荷值：").append(String.format("%.1f MW", alert.getCurrentValue())).append("\n");
            report.append("阈值：").append(String.format("%.0f MW", alert.getThresholdValue())).append("\n");
        }
        report.append("\n");

        // 二、问题研判
        report.append("二、问题研判\n");
        report.append("根据系统记录，本次风险主要表现为");
        if (alert != null) {
            report.append("负荷").append(String.format("%.1f MW", alert.getCurrentValue()))
                  .append(" 超过 ").append(alert.getLevel()).append(" 告警阈值")
                  .append(String.format("%.0f MW", alert.getThresholdValue())).append("。");
        } else {
            report.append("基于预测数据的预警工单，建议提前关注。");
        }
        report.append("\n调度侧建议：根据告警级别和趋势，评估是否需要调峰响应。\n");
        report.append("运维侧建议：核查数据采集通道和告警规则配置，确认是否为持续异常。\n");
        report.append("\n");

        // 三、处理过程
        report.append("三、处理过程\n");
        report.append("运维人员记录：\n");
        report.append(operatorText).append("\n");
        report.append("\n");

        // 四、处理结果
        report.append("四、处理结果\n");
        String status = ticket.getStatus();
        if ("RESOLVED".equals(status) || "CLOSED".equals(status)) {
            report.append("当前处理结论：工单已").append("RESOLVED".equals(status) ? "解决" : "关闭").append("。\n");
        } else {
            report.append("当前处理结论：工单仍在处理中（").append(status != null ? status : "未知").append("）。\n");
        }
        if (ticket.getResolution() != null && !ticket.getResolution().isBlank()) {
            report.append("处理结论：").append(ticket.getResolution()).append("\n");
        }
        report.append("风险是否解除：").append("RESOLVED".equals(status) || "CLOSED".equals(status) ? "是" : "否").append("\n");
        report.append("是否建议继续观察：").append("CLOSED".equals(status) ? "否" : "建议持续监测负荷变化").append("\n");
        report.append("\n");

        // 五、后续建议
        report.append("五、后续建议\n");
        if (alert != null) {
            String level = alert.getLevel();
            if ("RED".equals(level)) {
                report.append("1. 确认调峰措施已执行，复核负荷是否回落至安全区间。\n");
                report.append("2. 检查关联设备运行状态，排除设备异常。\n");
                report.append("3. 如频繁触发，建议重新评估阈值配置。\n");
            } else if ("ORANGE".equals(level)) {
                report.append("1. 持续监测未来24小时负荷趋势。\n");
                report.append("2. 如预测峰值接近红色区间，提前准备调峰预案。\n");
                report.append("3. 检查数据采集通道是否存在波动。\n");
            } else {
                report.append("1. 持续观察负荷变化趋势。\n");
                report.append("2. 如预测峰值上升，评估是否需要升级响应。\n");
                report.append("3. 检查告警规则配置是否合理。\n");
            }
        } else {
            report.append("1. 密切关注预测峰值变化。\n");
            report.append("2. 如实际负荷接近预测峰值，评估是否需要创建正式工单。\n");
            report.append("3. 确认预测模型最新批次数据是否有效。\n");
        }

        log.info("工单报告已生成: ticketId={}, ticketNo={}", ticketId, ticket.getTicketNo());

        return Map.of(
                "ticketId", ticketId,
                "ticketNo", ticket.getTicketNo(),
                "report", report.toString(),
                "source", "RULE_BASED_AGENT",
                "generatedAt", LocalDateTime.now().format(FMT)
        );
    }
}
