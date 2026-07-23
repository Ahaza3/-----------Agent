package com.powerload.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerload.common.R;
import com.powerload.dto.request.CreateTicketRequest;
import com.powerload.dto.request.CreatePrewarningTicketRequest;
import com.powerload.dto.request.TicketAssignRequest;
import com.powerload.dto.request.TicketFeedbackMetricsFilter;
import com.powerload.dto.request.TicketFeedbackRequest;
import com.powerload.dto.request.TicketResolveRequest;
import com.powerload.dto.response.AssigneeInfo;
import com.powerload.dto.response.TicketFeedbackMetricsResponse;
import com.powerload.dto.response.TicketFeedbackResponse;
import com.powerload.entity.AlertTicket;
import com.powerload.entity.AlertTicketAction;
import com.powerload.security.SysUserPrincipal;
import com.powerload.service.TicketService;
import com.powerload.service.TicketFeedbackService;
import com.powerload.ticket.TicketReportService;
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
    private final TicketFeedbackService ticketFeedbackService;
    private final TicketReportService ticketReportService;

    /* ─── Query ─── */

    @GetMapping("/tickets/assignees")
    public R<List<AssigneeInfo>> assignees() {
        return R.ok(ticketService.listAssignees());
    }

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

    @GetMapping("/tickets/{id}/feedback")
    public R<TicketFeedbackResponse> feedback(@PathVariable Long id) {
        return R.ok(ticketFeedbackService.getFeedback(id));
    }

    @PutMapping("/tickets/{id}/feedback")
    @PreAuthorize("hasAnyRole('OPERATOR','SYSTEM_ADMIN')")
    public R<TicketFeedbackResponse> saveFeedback(@PathVariable Long id,
                                                   @Valid @RequestBody TicketFeedbackRequest request,
                                                   @AuthenticationPrincipal SysUserPrincipal user) {
        return R.ok(ticketFeedbackService.save(id, request, user));
    }

    @GetMapping("/tickets/feedback/metrics")
    public R<TicketFeedbackMetricsResponse> feedbackMetrics(
            @ModelAttribute TicketFeedbackMetricsFilter filter) {
        return R.ok(ticketFeedbackService.metrics(filter));
    }

    /* ─── Write ─── */

    @GetMapping("/alerts/{alertId}/ticket")
    public R<AlertTicket> getByAlert(@PathVariable Long alertId) {
        AlertTicket t = ticketService.getByAlertId(alertId);
        // 无工单时返回 null（不抛异常，由前端判断）
        return R.ok(t);
    }

    @PostMapping("/alerts/{alertId}/ticket")
    public R<AlertTicket> create(@PathVariable Long alertId,
                                  @Valid @RequestBody CreateTicketRequest req,
                                  @AuthenticationPrincipal SysUserPrincipal user) {
        return R.ok(ticketService.create(alertId, req.getSummary(), req.getAssigneeUserId(), user));
    }

    @PostMapping("/tickets/prewarning")
    @PreAuthorize("hasAnyRole('DISPATCHER','SYSTEM_ADMIN')")
    public R<AlertTicket> createPrewarning(@Valid @RequestBody CreatePrewarningTicketRequest req,
                                           @AuthenticationPrincipal SysUserPrincipal user) {
        return R.ok(ticketService.createPrewarning(
                req.getSummary(), req.getRiskLevel(), req.getForecastTime(), req.getExpectedLoad(), user));
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

    /* ─── 工单处理报告 ─── */

    @PostMapping("/tickets/{id}/report")
    public R<Map<String, Object>> generateReport(@PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, String> body) {
        String operatorNote = body != null ? body.get("operatorNote") : null;
        return R.ok(ticketReportService.generateReport(id, operatorNote));
    }
}
