package com.powerload.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.audit.AuditLog;
import com.powerload.dto.request.TicketFeedbackMetricsFilter;
import com.powerload.dto.request.TicketFeedbackRequest;
import com.powerload.dto.response.TicketFeedbackMetricsResponse;
import com.powerload.dto.response.TicketFeedbackResponse;
import com.powerload.entity.AlertEvent;
import com.powerload.entity.AlertTicket;
import com.powerload.entity.TicketFeedback;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.mapper.AlertTicketMapper;
import com.powerload.mapper.TicketFeedbackMapper;
import com.powerload.security.SysUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketFeedbackService {

    private static final Set<String> CLASSIFICATIONS = Set.of(
            "TRUE_ALERT", "FALSE_POSITIVE", "DUPLICATE", "TEST", "INCONCLUSIVE");
    private static final Set<String> ROOT_CAUSES = Set.of(
            "EQUIPMENT", "DATA_QUALITY", "WEATHER", "LOAD_CHANGE",
            "RULE_CONFIG", "TOPOLOGY_SIMULATION", "UNKNOWN");
    private static final Set<String> EFFECTIVENESS = Set.of(
            "EFFECTIVE", "PARTIAL", "INEFFECTIVE", "NOT_APPLICABLE");
    private static final Set<String> FEEDBACK_ROLES = Set.of("OPERATOR", "SYSTEM_ADMIN");

    private final TicketFeedbackMapper feedbackMapper;
    private final AlertTicketMapper ticketMapper;
    private final AlertEventMapper alertEventMapper;

    public TicketFeedbackResponse getFeedback(Long ticketId) {
        AlertTicket ticket = requireTicket(ticketId);
        TicketFeedback feedback = feedbackMapper.selectByTicketId(ticketId);
        return toResponse(ticket, feedback);
    }

    @Transactional
    @AuditLog(module = "工单管理", action = "保存结构化反馈")
    public TicketFeedbackResponse save(Long ticketId, TicketFeedbackRequest request,
                                       SysUserPrincipal user) {
        AlertTicket ticket = requireTicket(ticketId);
        authorizeOperator(ticket, user);
        validateSource(ticket);
        validateRequest(request);

        TicketFeedback feedback = feedbackMapper.selectByTicketId(ticketId);
        boolean insert = feedback == null;
        boolean closedCorrection = !insert && "CLOSED".equals(ticket.getStatus()) && isAdmin(user);
        if (insert) {
            feedback = new TicketFeedback();
            feedback.setTicketId(ticketId);
            feedback.setCreatedAt(LocalDateTime.now());
        } else if ("CLOSED".equals(ticket.getStatus()) && !isAdmin(user)) {
            throw new IllegalStateException("已关闭工单反馈只读，需系统管理员审计修正");
        }

        feedback.setAlertId(ticket.getAlertId());
        feedback.setSourceType(normalizeSourceType(ticket));
        feedback.setAlertClassification(request.getAlertClassification().trim().toUpperCase(Locale.ROOT));
        feedback.setRootCauseCode(request.getRootCauseCode().trim().toUpperCase(Locale.ROOT));
        feedback.setRootCauseDetail(trimToNull(request.getRootCauseDetail()));
        feedback.setActionsTaken(JSONUtil.toJsonStr(request.getActionsTaken().stream()
                .map(String::trim).toList()));
        feedback.setActionDetail(trimToNull(request.getActionDetail()));
        feedback.setImpactLoadMw(request.getImpactLoadMw().setScale(3, RoundingMode.HALF_UP));
        feedback.setEffectiveness(request.getEffectiveness().trim().toUpperCase(Locale.ROOT));
        if (!closedCorrection) {
            feedback.setOperatorUserId(user.getUserId());
            feedback.setOperatorName(user.getUsername());
        }
        feedback.setUpdatedAt(LocalDateTime.now());

        if (insert) {
            feedbackMapper.insert(feedback);
        } else {
            feedbackMapper.updateById(feedback);
        }
        return toResponse(ticket, feedback);
    }

    /**
     * 关闭前在同一事务中调用：只读校验，不修改反馈字段。
     */
    public TicketFeedback requireCompleteForClose(AlertTicket ticket, SysUserPrincipal reviewer) {
        validateSource(ticket);
        TicketFeedback feedback = feedbackMapper.selectByTicketId(ticket.getId());
        if (feedback == null) {
            throw new IllegalStateException("关闭工单前必须提交完整结构化反馈");
        }
        validatePersisted(feedback);
        if (Objects.equals(feedback.getOperatorUserId(), reviewer.getUserId())) {
            throw new IllegalStateException("处理人和复核人不能为同一用户");
        }
        return feedback;
    }

    /**
     * 关闭成功前由 TicketService 调用，事务失败会与工单状态一起回滚。
     */
    public void markReviewed(TicketFeedback feedback, SysUserPrincipal reviewer) {
        feedback.setReviewerUserId(reviewer.getUserId());
        feedback.setReviewerName(reviewer.getUsername());
        feedback.setReviewedAt(LocalDateTime.now());
        feedback.setUpdatedAt(LocalDateTime.now());
        if (feedbackMapper.updateById(feedback) != 1) {
            throw new IllegalStateException("反馈已被其他用户更新，请刷新后重试");
        }
    }

    public Map<String, Object> readOnlySummary(Long ticketId) {
        AlertTicket ticket = requireTicket(ticketId);
        TicketFeedback feedback = feedbackMapper.selectByTicketId(ticketId);
        TicketFeedbackResponse response = toResponse(ticket, feedback);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("feedbackStatus", response.getFeedbackStatus());
        summary.put("alertClassification", response.getAlertClassification());
        summary.put("rootCauseCode", response.getRootCauseCode());
        summary.put("actionsTaken", response.getActionsTaken());
        summary.put("impactLoadMw", response.getImpactLoadMw());
        summary.put("effectiveness", response.getEffectiveness());
        summary.put("operatorName", response.getOperatorName());
        summary.put("reviewerName", response.getReviewerName());
        summary.put("reviewedAt", response.getReviewedAt());
        summary.put("alertEvidence", response.getAlertEvidence());
        return summary;
    }

    public TicketFeedbackMetricsResponse metrics(TicketFeedbackMetricsFilter filter) {
        TicketFeedbackMetricsFilter safeFilter = filter == null ? new TicketFeedbackMetricsFilter() : filter;
        TicketFeedbackMetricsResponse result = new TicketFeedbackMetricsResponse();
        result.setStart(safeFilter.getStart());
        result.setEnd(safeFilter.getEnd());
        result.setNodeId(safeFilter.getNodeId());
        result.setRuleId(safeFilter.getRuleId());
        result.setRuleVersion(safeFilter.getRuleVersion());
        result.setSourceType(safeFilter.getSourceType());
        result.setAlertClassification(safeFilter.getAlertClassification());
        result.setRootCauseCode(safeFilter.getRootCauseCode());
        result.setEffectiveness(safeFilter.getEffectiveness());

        List<AlertTicket> closedTickets = ticketMapper.selectList(new LambdaQueryWrapper<AlertTicket>()
                .eq(AlertTicket::getStatus, "CLOSED"));
        Map<Long, TicketFeedback> feedbackByTicket = feedbackMapper.selectList(
                        new LambdaQueryWrapper<TicketFeedback>())
                .stream().collect(Collectors.toMap(TicketFeedback::getTicketId, f -> f, (a, b) -> b));
        Set<Long> alertIds = closedTickets.stream().map(AlertTicket::getAlertId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AlertEvent> alerts = alertIds.isEmpty() ? Map.of()
                : alertEventMapper.selectBatchIds(alertIds).stream()
                .collect(Collectors.toMap(AlertEvent::getId, a -> a));

        boolean hasFeedbackFilter = safeFilter.getAlertClassification() != null
                || safeFilter.getRootCauseCode() != null || safeFilter.getEffectiveness() != null;
        for (AlertTicket ticket : closedTickets) {
            if (!matchesTicketFilter(ticket, safeFilter)) continue;
            AlertEvent alert = ticket.getAlertId() == null ? null : alerts.get(ticket.getAlertId());
            if (!matchesAlertFilter(alert, safeFilter)) continue;
            TicketFeedback feedback = feedbackByTicket.get(ticket.getId());
            if (feedback == null && hasFeedbackFilter) continue;
            if (feedback != null && !matchesFeedbackFilter(feedback, safeFilter)) continue;

            result.setTotalClosedTickets(result.getTotalClosedTickets() + 1);
            if (feedback == null) {
                result.setFeedbackMissingCount(result.getFeedbackMissingCount() + 1);
                continue;
            }
            result.setSampleCount(result.getSampleCount() + 1);
            result.setTotalImpactLoadMw(result.getTotalImpactLoadMw().add(
                    feedback.getImpactLoadMw() == null ? BigDecimal.ZERO : feedback.getImpactLoadMw()));
            switch (feedback.getAlertClassification()) {
                case "TRUE_ALERT" -> result.setTrueAlertCount(result.getTrueAlertCount() + 1);
                case "FALSE_POSITIVE" -> result.setFalsePositiveCount(result.getFalsePositiveCount() + 1);
                case "DUPLICATE" -> result.setDuplicateCount(result.getDuplicateCount() + 1);
                case "INCONCLUSIVE" -> result.setInconclusiveCount(result.getInconclusiveCount() + 1);
                default -> { /* TEST 不伪装成真实告警分类 */ }
            }
            switch (feedback.getEffectiveness()) {
                case "EFFECTIVE" -> result.setEffectiveCount(result.getEffectiveCount() + 1);
                case "PARTIAL" -> result.setPartialCount(result.getPartialCount() + 1);
                case "INEFFECTIVE" -> result.setIneffectiveCount(result.getIneffectiveCount() + 1);
                default -> { }
            }
        }
        long denominator = result.getTrueAlertCount() + result.getFalsePositiveCount();
        if (denominator > 0) {
            result.setAlertAccuracyPercent(BigDecimal.valueOf(result.getTrueAlertCount() * 100.0 / denominator)
                    .setScale(2, RoundingMode.HALF_UP));
        }
        return result;
    }

    private boolean matchesTicketFilter(AlertTicket ticket, TicketFeedbackMetricsFilter filter) {
        if (filter.getStart() != null && (ticket.getClosedAt() == null
                || ticket.getClosedAt().isBefore(filter.getStart()))) return false;
        if (filter.getEnd() != null && (ticket.getClosedAt() == null
                || ticket.getClosedAt().isAfter(filter.getEnd()))) return false;
        return filter.getSourceType() == null
                || filter.getSourceType().equalsIgnoreCase(normalizeSourceType(ticket));
    }

    private boolean matchesAlertFilter(AlertEvent alert, TicketFeedbackMetricsFilter filter) {
        if (filter.getNodeId() == null && filter.getRuleId() == null && filter.getRuleVersion() == null) {
            return true;
        }
        if (alert == null) return false;
        return (filter.getNodeId() == null || Objects.equals(filter.getNodeId(), alert.getNodeId()))
                && (filter.getRuleId() == null || Objects.equals(filter.getRuleId(), alert.getRuleId()))
                && (filter.getRuleVersion() == null || filter.getRuleVersion().equals(alert.getRuleVersion()));
    }

    private boolean matchesFeedbackFilter(TicketFeedback feedback, TicketFeedbackMetricsFilter filter) {
        return (filter.getAlertClassification() == null
                || filter.getAlertClassification().equalsIgnoreCase(feedback.getAlertClassification()))
                && (filter.getRootCauseCode() == null
                || filter.getRootCauseCode().equalsIgnoreCase(feedback.getRootCauseCode()))
                && (filter.getEffectiveness() == null
                || filter.getEffectiveness().equalsIgnoreCase(feedback.getEffectiveness()));
    }

    private TicketFeedbackResponse toResponse(AlertTicket ticket, TicketFeedback feedback) {
        TicketFeedbackResponse response = new TicketFeedbackResponse();
        response.setTicketId(ticket.getId());
        response.setAlertId(ticket.getAlertId());
        response.setSourceType(normalizeSourceType(ticket));
        response.setFeedbackStatus(feedback == null
                ? ("CLOSED".equals(ticket.getStatus()) ? "LEGACY_MISSING" : "MISSING")
                : "COMPLETED");
        if (feedback != null) {
            response.setAlertClassification(feedback.getAlertClassification());
            response.setRootCauseCode(feedback.getRootCauseCode());
            response.setRootCauseDetail(feedback.getRootCauseDetail());
            response.setActionsTaken(parseActions(feedback.getActionsTaken()));
            response.setActionDetail(feedback.getActionDetail());
            response.setImpactLoadMw(feedback.getImpactLoadMw());
            response.setEffectiveness(feedback.getEffectiveness());
            response.setOperatorUserId(feedback.getOperatorUserId());
            response.setOperatorName(feedback.getOperatorName());
            response.setReviewerUserId(feedback.getReviewerUserId());
            response.setReviewerName(feedback.getReviewerName());
            response.setReviewedAt(feedback.getReviewedAt());
            response.setCreatedAt(feedback.getCreatedAt());
            response.setUpdatedAt(feedback.getUpdatedAt());
        }
        response.setAlertEvidence(buildEvidence(ticket));
        return response;
    }

    private Map<String, Object> buildEvidence(AlertTicket ticket) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("sourceType", normalizeSourceType(ticket));
        if (ticket.getAlertId() == null) {
            evidence.put("evidenceStatus", "PREWARNING_NO_REAL_ALERT");
            return evidence;
        }
        AlertEvent alert = alertEventMapper.selectById(ticket.getAlertId());
        if (alert == null) {
            evidence.put("evidenceStatus", "ALERT_NOT_FOUND");
            return evidence;
        }
        evidence.put("evidenceStatus", "SNAPSHOT_SUMMARY");
        evidence.put("alertLevel", alert.getLevel());
        evidence.put("nodeId", alert.getNodeId());
        evidence.put("currentValue", alert.getCurrentValue());
        evidence.put("thresholdValue", alert.getThresholdValue());
        evidence.put("dataSource", alert.getDataSource());
        evidence.put("triggerTime", alert.getTriggerTime());
        evidence.put("ruleVersion", alert.getRuleVersion());
        evidence.put("sourceObservedAt", alert.getSourceObservedAt());
        evidence.put("impactLoadMw", alert.getImpactLoadMw());
        return evidence;
    }

    private void authorizeOperator(AlertTicket ticket, SysUserPrincipal user) {
        if (user == null || !FEEDBACK_ROLES.contains(user.getRole())) {
            throw new IllegalStateException("只有工单处理角色可以提交或修改反馈");
        }
        if ("CLOSED".equals(ticket.getStatus()) && !isAdmin(user)) {
            throw new IllegalStateException("已关闭工单反馈只读，需系统管理员审计修正");
        }
        if ("OPERATOR".equals(user.getRole())
                && !Objects.equals(user.getUserId(), ticket.getAssigneeUserId())) {
            throw new IllegalStateException("只有当前工单处理人可以提交反馈");
        }
    }

    private void validateSource(AlertTicket ticket) {
        if ("ALERT".equalsIgnoreCase(normalizeSourceType(ticket))) {
            if (ticket.getAlertId() == null || alertEventMapper.selectById(ticket.getAlertId()) == null) {
                throw new IllegalArgumentException("告警工单未关联真实告警，不能伪造反馈");
            }
        }
    }

    private void validateRequest(TicketFeedbackRequest request) {
        if (request == null) throw new IllegalArgumentException("反馈内容不能为空");
        String classification = upper(request.getAlertClassification());
        String rootCause = upper(request.getRootCauseCode());
        String effectiveness = upper(request.getEffectiveness());
        if (!CLASSIFICATIONS.contains(classification)) throw new IllegalArgumentException("告警分类不合法");
        if (!ROOT_CAUSES.contains(rootCause)) throw new IllegalArgumentException("根因分类不合法");
        if (!EFFECTIVENESS.contains(effectiveness)) throw new IllegalArgumentException("处置效果不合法");
        if ("UNKNOWN".equals(rootCause) && isBlank(request.getRootCauseDetail())) {
            throw new IllegalArgumentException("根因选择 UNKNOWN 时必须填写根因说明");
        }
        if (request.getActionsTaken() == null || request.getActionsTaken().isEmpty()
                || request.getActionsTaken().size() > 20) {
            throw new IllegalArgumentException("结构化处置措施至少一项且不超过20项");
        }
        for (String action : request.getActionsTaken()) {
            if (isBlank(action) || action.trim().length() > 200) {
                throw new IllegalArgumentException("每项处置措施不能为空且不超过200字");
            }
        }
        if (request.getImpactLoadMw() == null || request.getImpactLoadMw().signum() < 0
                || request.getImpactLoadMw().scale() > 3
                || request.getImpactLoadMw().precision() > 12) {
            throw new IllegalArgumentException("影响负荷必须为不超过三位小数的非负数");
        }
        if ("NOT_APPLICABLE".equals(effectiveness) && isBlank(request.getActionDetail())) {
            throw new IllegalArgumentException("处置效果为 NOT_APPLICABLE 时必须说明原因");
        }
        if (request.getImpactLoadMw().signum() == 0
                && (isBlank(request.getActionDetail()) || !request.getActionDetail().contains("无影响"))) {
            throw new IllegalArgumentException("影响负荷为0时必须在处置说明中明确“无影响”");
        }
        if (!isBlank(request.getRootCauseDetail()) && request.getRootCauseDetail().length() > 1000) {
            throw new IllegalArgumentException("根因说明过长");
        }
        if (!isBlank(request.getActionDetail()) && request.getActionDetail().length() > 2000) {
            throw new IllegalArgumentException("处置说明过长");
        }
    }

    private void validatePersisted(TicketFeedback feedback) {
        TicketFeedbackRequest request = new TicketFeedbackRequest();
        request.setAlertClassification(feedback.getAlertClassification());
        request.setRootCauseCode(feedback.getRootCauseCode());
        request.setRootCauseDetail(feedback.getRootCauseDetail());
        request.setActionsTaken(parseActions(feedback.getActionsTaken()));
        request.setActionDetail(feedback.getActionDetail());
        request.setImpactLoadMw(feedback.getImpactLoadMw());
        request.setEffectiveness(feedback.getEffectiveness());
        validateRequest(request);
    }

    private AlertTicket requireTicket(Long ticketId) {
        AlertTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) throw new IllegalArgumentException("工单不存在");
        return ticket;
    }

    private static String normalizeSourceType(AlertTicket ticket) {
        if (ticket.getSourceType() != null && !ticket.getSourceType().isBlank()) {
            return ticket.getSourceType().trim().toUpperCase(Locale.ROOT);
        }
        return ticket.getAlertId() == null ? "MANUAL" : "ALERT";
    }

    private static boolean isAdmin(SysUserPrincipal user) {
        return user != null && "SYSTEM_ADMIN".equals(user.getRole());
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<String> parseActions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSONUtil.parseArray(json).toList(String.class);
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
