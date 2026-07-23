package com.powerload.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerload.audit.AuditLog;
import com.powerload.dto.response.GridResponsibility;
import com.powerload.entity.*;
import com.powerload.mapper.*;
import com.powerload.security.SysUserPrincipal;
import com.powerload.websocket.PushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerload.dto.response.AssigneeInfo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TicketService {

    private final AlertTicketMapper ticketMapper;
    private final AlertTicketActionMapper actionMapper;
    private final AlertEventMapper alertEventMapper;
    private final SysUserMapper sysUserMapper;
    private final PushService pushService;
    private final GridTopologyService gridTopologyService;
    private final TicketFeedbackService ticketFeedbackService;

    public TicketService(AlertTicketMapper ticketMapper,
                         AlertTicketActionMapper actionMapper,
                         AlertEventMapper alertEventMapper,
                         SysUserMapper sysUserMapper,
                         PushService pushService) {
        this(ticketMapper, actionMapper, alertEventMapper, sysUserMapper, pushService, null, null);
    }

    public TicketService(AlertTicketMapper ticketMapper,
                         AlertTicketActionMapper actionMapper,
                         AlertEventMapper alertEventMapper,
                         SysUserMapper sysUserMapper,
                         PushService pushService,
                         GridTopologyService gridTopologyService) {
        this(ticketMapper, actionMapper, alertEventMapper, sysUserMapper, pushService,
                gridTopologyService, null);
    }

    @Autowired
    public TicketService(AlertTicketMapper ticketMapper,
                         AlertTicketActionMapper actionMapper,
                         AlertEventMapper alertEventMapper,
                         SysUserMapper sysUserMapper,
                         PushService pushService,
                         GridTopologyService gridTopologyService,
                         TicketFeedbackService ticketFeedbackService) {
        this.ticketMapper = ticketMapper;
        this.actionMapper = actionMapper;
        this.alertEventMapper = alertEventMapper;
        this.sysUserMapper = sysUserMapper;
        this.pushService = pushService;
        this.gridTopologyService = gridTopologyService;
        this.ticketFeedbackService = ticketFeedbackService;
    }

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "PENDING",     Set.of("ASSIGNED", "IN_PROGRESS", "CANCELLED"),
        "ASSIGNED",    Set.of("IN_PROGRESS", "CANCELLED"),
        "IN_PROGRESS", Set.of("RESOLVED"),
        "RESOLVED",    Set.of("CLOSED")
    );
    private static final Set<String> FINAL_STATUS = Set.of("CLOSED", "CANCELLED");

    /* ─── Query ─── */

    public Page<AlertTicket> list(String status, String priority, Long assigneeUserId,
                                   String alertLevel, String keyword, int page, int size) {
        var w = new LambdaQueryWrapper<AlertTicket>();
        w.eq(status != null, AlertTicket::getStatus, status)
         .eq(priority != null, AlertTicket::getPriority, priority);
        // assigneeUserId 过滤：包含分配给该用户的 + 所有 PENDING（可被认领）
        if (assigneeUserId != null) {
            w.and(q -> q.eq(AlertTicket::getAssigneeUserId, assigneeUserId)
                        .or().eq(AlertTicket::getStatus, "PENDING"));
        }
        if (keyword != null && !keyword.isBlank())
            w.and(q -> q.like(AlertTicket::getTicketNo, keyword).or().like(AlertTicket::getSummary, keyword));
        if (alertLevel != null) {
            var ids = alertEventMapper.selectList(
                new LambdaQueryWrapper<AlertEvent>().eq(AlertEvent::getLevel, alertLevel)
            ).stream().map(AlertEvent::getId).toList();
            if (ids.isEmpty()) return new Page<>(page, size);
            w.in(AlertTicket::getAlertId, ids);
        }
        w.orderByDesc(AlertTicket::getCreatedAt);
        Page<AlertTicket> result = ticketMapper.selectPage(new Page<>(page, size), w);
        result.getRecords().forEach(this::applySla);
        return result;
    }

    public AlertTicket getById(Long id) {
        AlertTicket t = ticketMapper.selectById(id);
        if (t == null) throw new IllegalArgumentException("工单不存在");
        applySla(t);
        return t;
    }

    public AlertTicket getByAlertId(Long alertId) {
        return ticketMapper.selectOne(
            new LambdaQueryWrapper<AlertTicket>().eq(AlertTicket::getAlertId, alertId));
    }

    public AlertTicket getByTicketNo(String no) {
        return ticketMapper.selectOne(
            new LambdaQueryWrapper<AlertTicket>().eq(AlertTicket::getTicketNo, no));
    }

    public List<AlertTicketAction> getActions(Long ticketId) {
        return actionMapper.selectList(new LambdaQueryWrapper<AlertTicketAction>()
            .eq(AlertTicketAction::getTicketId, ticketId).orderByAsc(AlertTicketAction::getCreatedAt));
    }

    /* ─── Create ─── */

    public AlertTicket create(Long alertId, String summary, SysUserPrincipal user) {
        return create(alertId, summary, null, user);
    }

    @Transactional
    @AuditLog(module = "工单管理", action = "创建工单")
    public AlertTicket create(Long alertId, String summary, Long requestedAssigneeId,
                              SysUserPrincipal user) {
        AlertEvent alert = alertEventMapper.selectById(alertId);
        if (alert == null) throw new IllegalArgumentException("告警不存在");
        if (ticketMapper.selectCount(new LambdaQueryWrapper<AlertTicket>()
                .eq(AlertTicket::getAlertId, alertId)) > 0)
            throw new IllegalStateException("该告警已存在工单");

        String no = "TK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        AlertTicket t = new AlertTicket();
        t.setTicketNo(no); t.setAlertId(alertId);
        t.setSourceType("ALERT");
        t.setRiskLevel(alert.getLevel());
        t.setPriority(mapPriority(alert.getLevel()));
        t.setSummary(summary);
        t.setCreatedBy(user.getUserId()); t.setCreatedByName(user.getUsername());
        t.setCreatedAt(LocalDateTime.now());

        AssigneeRoute route = requestedAssigneeId != null
                ? validateAssignee(requestedAssigneeId)
                : resolveAssignee(alert);
        if (route != null) {
            t.setAssigneeUserId(route.userId());
            t.setAssigneeName(route.displayName());
            t.setAssignedAt(LocalDateTime.now());
            t.setStatus("ASSIGNED");
        } else {
            t.setStatus("PENDING");
        }
        try { ticketMapper.insert(t); }
        catch (DuplicateKeyException e) { throw new IllegalStateException("该告警已存在工单"); }

        String routeNote = route == null
                ? "待调度中心认领"
                : "已按拓扑责任域自动分派给 " + route.displayName();
        addAction(t.getId(), "CREATE", null, t.getStatus(), user, summary + "\n" + routeNote);
        pushUpdate(t, "ticket_created");
        return t;
    }

    @Transactional
    @AuditLog(module = "工单管理", action = "创建预警工单")
    public AlertTicket createPrewarning(String summary, String riskLevel,
                                        LocalDateTime forecastTime, Float expectedLoad,
                                        SysUserPrincipal user) {
        if (summary == null || summary.isBlank()) throw new IllegalArgumentException("工单概要不能为空");
        String normalizedRisk = normalizeRiskLevel(riskLevel);
        if (forecastTime == null) throw new IllegalArgumentException("预测时间不能为空");
        if (expectedLoad == null || expectedLoad <= 0) throw new IllegalArgumentException("预测负荷必须大于 0");

        String no = "PW-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
        AlertTicket t = new AlertTicket();
        t.setTicketNo(no);
        t.setAlertId(null);
        t.setSourceType("PREWARNING");
        t.setRiskLevel(normalizedRisk);
        t.setForecastTime(forecastTime);
        t.setExpectedLoad(expectedLoad);
        t.setPriority(mapPriority(normalizedRisk));
        t.setStatus("PENDING");
        t.setSummary(summary);
        t.setCreatedBy(user.getUserId());
        t.setCreatedByName(user.getUsername());
        t.setCreatedAt(LocalDateTime.now());
        ticketMapper.insert(t);

        addAction(t.getId(), "CREATE", null, "PENDING", user,
                "基于预测风险创建预警工单: " + summary);
        pushUpdate(t, "ticket_created");
        return t;
    }

    /* ─── Assign ─── */

    @Transactional
    @AuditLog(module = "工单管理", action = "指派工单")
    public AlertTicket assign(Long ticketId, Long assigneeId, SysUserPrincipal user) {
        AlertTicket t = validate(ticketId, "ASSIGNED", user);
        SysUser au = sysUserMapper.selectById(assigneeId);
        if (au == null) throw new IllegalArgumentException("用户不存在");
        if (!"OPERATOR".equals(au.getRole())) throw new IllegalArgumentException("只能指派给运维人员");
        if (au.getIsActive() != 1) throw new IllegalArgumentException("该运维人员已被禁用");
        String from = t.getStatus();
        t.setAssigneeUserId(assigneeId); t.setAssigneeName(au.getUsername());
        t.setAssignedAt(LocalDateTime.now()); t.setStatus("ASSIGNED");
        ticketMapper.updateById(t);
        addAction(ticketId, "ASSIGN", from, "ASSIGNED", user, "指派给 " + au.getUsername());
        pushUpdate(t, "ticket_assigned");
        return t;
    }

    /* ─── Claim ─── */

    @Transactional
    @AuditLog(module = "工单管理", action = "认领工单")
    public AlertTicket claim(Long ticketId, SysUserPrincipal user) {
        AlertTicket t = ticketMapper.selectById(ticketId);
        if (t == null) throw new IllegalArgumentException("工单不存在");
        if (FINAL_STATUS.contains(t.getStatus()))
            throw new IllegalStateException("工单已终态，不可认领");
        if ("ASSIGNED".equals(t.getStatus()))
            throw new IllegalStateException("工单已指派，请直接开始处理");

        String from = t.getStatus();
        t.setAssigneeUserId(user.getUserId()); t.setAssigneeName(user.getUsername());
        t.setStartedAt(LocalDateTime.now()); t.setStatus("IN_PROGRESS");
        if (ticketMapper.updateById(t) != 1)
            throw new IllegalStateException("工单已被其他用户更新，请刷新后重试");
        addAction(ticketId, "CLAIM", from, "IN_PROGRESS", user, "认领并开始处理");
        pushUpdate(t, "ticket_claimed");
        return t;
    }

    /* ─── Start ─── */

    @Transactional
    @AuditLog(module = "工单管理", action = "开始处理")
    public AlertTicket start(Long ticketId, SysUserPrincipal user) {
        AlertTicket t = validate(ticketId, "IN_PROGRESS", user);
        if (!user.getUserId().equals(t.getAssigneeUserId()))
            throw new IllegalStateException("只有指定处理人可以开始处理");
        String from = t.getStatus();
        t.setStartedAt(LocalDateTime.now()); t.setStatus("IN_PROGRESS");
        if (ticketMapper.updateById(t) != 1)
            throw new IllegalStateException("工单已被其他用户更新，请刷新后重试");
        addAction(ticketId, "START", from, "IN_PROGRESS", user, "开始处理");
        pushUpdate(t, "ticket_started");
        return t;
    }

    /* ─── Resolve ─── */

    @Transactional
    @AuditLog(module = "工单管理", action = "标记解决")
    public AlertTicket resolve(Long ticketId, String resolution, SysUserPrincipal user) {
        AlertTicket t = validate(ticketId, "RESOLVED", user);
        if (!user.getUserId().equals(t.getAssigneeUserId()))
            throw new IllegalStateException("只有指定处理人可以标记解决");
        if (resolution == null || resolution.isBlank())
            throw new IllegalArgumentException("处理结果说明不能为空");
        t.setResolution(resolution); t.setResolvedAt(LocalDateTime.now());
        t.setStatus("RESOLVED");
        ticketMapper.updateById(t);
        AlertEvent alert = t.getAlertId() != null ? alertEventMapper.selectById(t.getAlertId()) : null;
        if (alert != null && alert.getResolvedAt() == null) {
            alert.setResolvedAt(LocalDateTime.now());
            alertEventMapper.updateById(alert);
        }
        addAction(ticketId, "RESOLVE", "IN_PROGRESS", "RESOLVED", user, resolution);
        pushUpdate(t, "ticket_resolved");
        return t;
    }

    /* ─── Close ─── */

    @Transactional
    @AuditLog(module = "工单管理", action = "关闭工单")
    public AlertTicket close(Long ticketId, SysUserPrincipal user) {
        if (user == null || !Set.of("DISPATCHER", "SYSTEM_ADMIN").contains(user.getRole())) {
            throw new IllegalStateException("只有调度员或系统管理员可以关闭工单");
        }
        AlertTicket t = ticketMapper.selectById(ticketId);
        if (t == null) throw new IllegalArgumentException("工单不存在");
        if ("CLOSED".equals(t.getStatus())) {
            return t;
        }
        if ("CANCELLED".equals(t.getStatus())) {
            throw new IllegalStateException("工单已取消，不可关闭");
        }
        if (!"RESOLVED".equals(t.getStatus())) {
            throw new IllegalStateException("只有已解决工单可以关闭");
        }
        if (ticketFeedbackService != null) {
            TicketFeedback feedback = ticketFeedbackService.requireCompleteForClose(t, user);
            ticketFeedbackService.markReviewed(feedback, user);
        }
        String from = t.getStatus();
        t.setClosedAt(LocalDateTime.now()); t.setStatus("CLOSED");
        if (ticketMapper.updateById(t) != 1) {
            throw new IllegalStateException("工单已被其他用户更新，请刷新后重试");
        }
        addAction(ticketId, "CLOSE", from, "CLOSED", user, "复核反馈完整并确认处置完成");
        pushUpdate(t, "ticket_closed");
        return t;
    }

    /* ─── Cancel ─── */

    @Transactional
    @AuditLog(module = "工单管理", action = "取消工单")
    public AlertTicket cancel(Long ticketId, String reason, SysUserPrincipal user) {
        AlertTicket t = validate(ticketId, "CANCELLED", user);
        String from = t.getStatus();
        t.setCancelledAt(LocalDateTime.now()); t.setStatus("CANCELLED");
        ticketMapper.updateById(t);
        addAction(ticketId, "CANCEL", from, "CANCELLED", user,
                reason != null && !reason.isBlank() ? reason : "无");
        pushUpdate(t, "ticket_cancelled");
        return t;
    }

    /* ─── Helpers ─── */

    private AlertTicket validate(Long ticketId, String target, SysUserPrincipal user) {
        AlertTicket t = ticketMapper.selectById(ticketId);
        if (t == null) throw new IllegalArgumentException("工单不存在");
        if (FINAL_STATUS.contains(t.getStatus()))
            throw new IllegalStateException("工单已终态，不可操作");
        Set<String> allowed = ALLOWED.get(t.getStatus());
        if (allowed == null || !allowed.contains(target))
            throw new IllegalStateException("不允许从 " + t.getStatus() + " 转换为 " + target);
        return t;
    }

    private void addAction(Long ticketId, String action, String from, String to,
                            SysUserPrincipal user, String note) {
        AlertTicketAction a = new AlertTicketAction();
        a.setTicketId(ticketId); a.setAction(action);
        a.setFromStatus(from); a.setToStatus(to);
        a.setOperatorId(user.getUserId()); a.setOperatorName(user.getUsername());
        a.setOperatorRole(user.getRole()); a.setNote(note);
        a.setCreatedAt(LocalDateTime.now());
        actionMapper.insert(a);
    }

    private String mapPriority(String level) {
        return switch (level) { case "RED" -> "URGENT"; case "ORANGE" -> "HIGH"; default -> "NORMAL"; };
    }

    private void applySla(AlertTicket ticket) {
        if (ticket.getCreatedAt() == null) return;
        int responseMinutes = switch (ticket.getPriority()) {
            case "URGENT" -> 5;
            case "HIGH" -> 15;
            default -> 30;
        };
        int processingMinutes = switch (ticket.getPriority()) {
            case "URGENT" -> 30;
            case "HIGH" -> 120;
            default -> 240;
        };
        ticket.setResponseDeadline(ticket.getCreatedAt().plusMinutes(responseMinutes));
        ticket.setProcessingDeadline(ticket.getCreatedAt().plusMinutes(processingMinutes));

        if (FINAL_STATUS.contains(ticket.getStatus())) {
            ticket.setSlaStatus("COMPLETED");
        } else if (ticket.getAssignedAt() == null && LocalDateTime.now().isAfter(ticket.getResponseDeadline())) {
            ticket.setSlaStatus("OVERDUE_RESPONSE");
        } else if (LocalDateTime.now().isAfter(ticket.getProcessingDeadline())) {
            ticket.setSlaStatus("OVERDUE_PROCESSING");
        } else {
            ticket.setSlaStatus("ON_TRACK");
        }
    }

    private String normalizeRiskLevel(String riskLevel) {
        if (riskLevel == null) return "YELLOW";
        String value = riskLevel.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("RED", "ORANGE", "YELLOW").contains(value)) {
            throw new IllegalArgumentException("风险级别必须为 RED/ORANGE/YELLOW");
        }
        return value;
    }

    private AssigneeRoute resolveAssignee(AlertEvent alert) {
        if (gridTopologyService == null || alert.getNodeId() == null) {
            return null;
        }
        GridResponsibility responsibility = gridTopologyService.resolveResponsibility(alert.getNodeId());
        if (responsibility == null || responsibility.isDispatchCenter()
                || responsibility.getAssigneeUserId() == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(responsibility.getAssigneeUserId());
        if (!isActiveOperator(user)) {
            return null;
        }
        return new AssigneeRoute(user.getId(),
                user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
    }

    private AssigneeRoute validateAssignee(Long assigneeId) {
        SysUser user = sysUserMapper.selectById(assigneeId);
        if (!isActiveOperator(user)) {
            throw new IllegalArgumentException("只能分派给启用中的运维人员");
        }
        return new AssigneeRoute(user.getId(),
                user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
    }

    private boolean isActiveOperator(SysUser user) {
        return user != null
                && "OPERATOR".equals(user.getRole())
                && Integer.valueOf(1).equals(user.getIsActive());
    }

    /* ─── Assignee 查询 ─── */

    /** 返回所有启用的 OPERATOR 用户（最小信息） */
    public List<AssigneeInfo> listAssignees() {
        List<SysUser> operators = sysUserMapper.selectList(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "OPERATOR")
                .eq(SysUser::getIsActive, 1));
        return operators.stream()
            .map(u -> new AssigneeInfo(u.getId(), u.getDisplayName(), u.getUsername(), u.getIsActive() == 1))
            .collect(Collectors.toList());
    }

    private void pushUpdate(AlertTicket t, String type) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", t.getId());
            data.put("ticketNo", t.getTicketNo());
            data.put("alertId", t.getAlertId());
            data.put("sourceType", t.getSourceType());
            data.put("status", t.getStatus());
            data.put("priority", t.getPriority());
            data.put("assigneeName", t.getAssigneeName() != null ? t.getAssigneeName() : "");
            data.put("updatedAt", t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : "");
            pushService.pushToTopic("/topic/tickets", Map.of("type", type, "data", data));
        } catch (Exception ignored) {}
    }

    private record AssigneeRoute(Long userId, String displayName) {
    }
}
