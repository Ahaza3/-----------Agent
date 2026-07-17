package com.powerload.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerload.common.R;
import com.powerload.dto.request.CreateTicketRequest;
import com.powerload.dto.request.TicketAssignRequest;
import com.powerload.dto.request.TicketResolveRequest;
import com.powerload.entity.AlertTicket;
import com.powerload.entity.AlertTicketAction;
import com.powerload.security.SysUserPrincipal;
import com.powerload.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    /* ─── Query ─── */

    @GetMapping("/tickets")
    public R<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long assigneeUserId,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AlertTicket> p = ticketService.list(status, priority, assigneeUserId, alertLevel, keyword, page, size);
        return R.ok(Map.of("records", p.getRecords(), "total", p.getTotal(),
                "page", p.getCurrent(), "size", p.getSize(), "pages", p.getPages()));
    }

    @GetMapping("/tickets/{id}")
    public R<AlertTicket> detail(@PathVariable Long id) {
        return R.ok(ticketService.getById(id));
    }

    @GetMapping("/tickets/{id}/actions")
    public R<List<AlertTicketAction>> actions(@PathVariable Long id) {
        return R.ok(ticketService.getActions(id));
    }

    /* ─── Write ─── */

    @GetMapping("/alerts/{alertId}/ticket")
    public R<AlertTicket> getByAlert(@PathVariable Long alertId) {
        AlertTicket t = ticketService.getByAlertId(alertId);
        if (t == null) throw new IllegalArgumentException("该告警暂无工单");
        return R.ok(t);
    }

    @PostMapping("/alerts/{alertId}/ticket")
    public R<AlertTicket> create(@PathVariable Long alertId,
                                  @Valid @RequestBody CreateTicketRequest req,
                                  @AuthenticationPrincipal SysUserPrincipal user) {
        return R.ok(ticketService.create(alertId, req.getSummary(), user));
    }

    @PutMapping("/tickets/{id}/assign")
    @PreAuthorize("hasAnyRole('DISPATCHER','SYSTEM_ADMIN')")
    public R<AlertTicket> assign(@PathVariable Long id,
                                  @Valid @RequestBody TicketAssignRequest req,
                                  @AuthenticationPrincipal SysUserPrincipal user) {
        return R.ok(ticketService.assign(id, req.getAssigneeUserId(), user));
    }

    @PutMapping("/tickets/{id}/claim")
    @PreAuthorize("hasAnyRole('OPERATOR','SYSTEM_ADMIN')")
    public R<AlertTicket> claim(@PathVariable Long id,
                                 @AuthenticationPrincipal SysUserPrincipal user) {
        return R.ok(ticketService.claim(id, user));
    }

    @PutMapping("/tickets/{id}/start")
    @PreAuthorize("hasAnyRole('OPERATOR','SYSTEM_ADMIN')")
    public R<AlertTicket> start(@PathVariable Long id,
                                 @AuthenticationPrincipal SysUserPrincipal user) {
        return R.ok(ticketService.start(id, user));
    }

    @PutMapping("/tickets/{id}/resolve")
    @PreAuthorize("hasAnyRole('OPERATOR','SYSTEM_ADMIN')")
    public R<AlertTicket> resolve(@PathVariable Long id,
                                   @Valid @RequestBody TicketResolveRequest req,
                                   @AuthenticationPrincipal SysUserPrincipal user) {
        return R.ok(ticketService.resolve(id, req.getResolution(), user));
    }

    @PutMapping("/tickets/{id}/close")
    @PreAuthorize("hasAnyRole('DISPATCHER','SYSTEM_ADMIN')")
    public R<AlertTicket> close(@PathVariable Long id,
                                 @AuthenticationPrincipal SysUserPrincipal user) {
        return R.ok(ticketService.close(id, user));
    }

    @PutMapping("/tickets/{id}/cancel")
    @PreAuthorize("hasAnyRole('DISPATCHER','SYSTEM_ADMIN')")
    public R<AlertTicket> cancel(@PathVariable Long id,
                                  @RequestBody(required = false) Map<String, String> body,
                                  @AuthenticationPrincipal SysUserPrincipal user) {
        String reason = body != null ? body.get("reason") : null;
        return R.ok(ticketService.cancel(id, reason, user));
    }
}
