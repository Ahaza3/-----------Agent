package com.powerload.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerload.audit.AuditLog;
import com.powerload.entity.*;
import com.powerload.mapper.*;
import com.powerload.security.SysUserPrincipal;
import com.powerload.websocket.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final AlertTicketMapper ticketMapper;
    private final AlertTicketActionMapper actionMapper;
    private final AlertEventMapper alertEventMapper;
    private final SysUserMapper sysUserMapper;
    private final PushService pushService;

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
        return ticketMapper.selectPage(new Page<>(page, size), w);
    }

    public AlertTicket getById(Long id) {
        AlertTicket t = ticketMapper.selectById(id);
        if (t == null) throw new IllegalArgumentException("工单不存在");
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

    @Transactional
    @AuditLog(module = "工单管理", action = "创建工单")
    public AlertTicket create(Long alertId, String summary, SysUserPrincipal user) {
        AlertEvent alert = alertEventMapper.selectById(alertId);
        if (alert == null) throw new IllegalArgumentException("告警不存在");
        if (ticketMapper.selectCount(new LambdaQueryWrapper<AlertTicket>()
                .eq(AlertTicket::getAlertId, alertId)) > 0)
            throw new IllegalStateException("该告警已存在工单");

        String no = "TK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        AlertTicket t = new AlertTicket();
        t.setTicketNo(no); t.setAlertId(alertId);
        t.setPriority(mapPriority(alert.getLevel()));
        t.setStatus("PENDING"); t.setSummary(summary);
        t.setCreatedBy(user.getUserId()); t.setCreatedByName(user.getUsername());
        t.setCreatedAt(LocalDateTime.now());
        try { ticketMapper.insert(t); }
        catch (DuplicateKeyException e) { throw new IllegalStateException("该告警已存在工单"); }

        addAction(t.getId(), "CREATE", null, "PENDING", user, summary);
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
        if ("ASSIGNED".equals(t.getStatus()) && !user.getUserId().equals(t.getAssigneeUserId()))
            throw new IllegalStateException("该工单已指派给其他人");

        String from = t.getStatus();
        t.setAssigneeUserId(user.getUserId()); t.setAssigneeName(user.getUsername());
        t.setStartedAt(LocalDateTime.now()); t.setStatus("IN_PROGRESS");
        ticketMapper.updateById(t);
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
        ticketMapper.updateById(t);
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
        AlertEvent alert = alertEventMapper.selectById(t.getAlertId());
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
        AlertTicket t = validate(ticketId, "CLOSED", user);
        String from = t.getStatus();
        t.setClosedAt(LocalDateTime.now()); t.setStatus("CLOSED");
        ticketMapper.updateById(t);
        addAction(ticketId, "CLOSE", from, "CLOSED", user, "确认处置完成并关闭");
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

    private void pushUpdate(AlertTicket t, String type) {
        try {
            pushService.pushToTopic("/topic/tickets", Map.of(
                "type", type, "data", Map.of(
                    "id", t.getId(), "ticketNo", t.getTicketNo(),
                    "alertId", t.getAlertId(), "status", t.getStatus(),
                    "priority", t.getPriority(),
                    "assigneeName", t.getAssigneeName() != null ? t.getAssigneeName() : "",
                    "updatedAt", t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : "")));
        } catch (Exception ignored) {}
    }
}
