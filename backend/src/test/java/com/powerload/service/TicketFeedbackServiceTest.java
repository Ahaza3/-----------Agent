package com.powerload.service;

import com.powerload.dto.request.TicketFeedbackMetricsFilter;
import com.powerload.dto.request.TicketFeedbackRequest;
import com.powerload.entity.AlertEvent;
import com.powerload.entity.AlertTicket;
import com.powerload.entity.TicketFeedback;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.mapper.AlertTicketMapper;
import com.powerload.mapper.TicketFeedbackMapper;
import com.powerload.security.SysUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TicketFeedbackServiceTest {

    private TicketFeedbackMapper feedbackMapper;
    private AlertTicketMapper ticketMapper;
    private AlertEventMapper alertEventMapper;
    private TicketFeedbackService service;

    private final SysUserPrincipal operator = new SysUserPrincipal(2L, "运维李四", "OPERATOR");
    private final SysUserPrincipal dispatcher = new SysUserPrincipal(1L, "调度张三", "DISPATCHER");
    private final SysUserPrincipal admin = new SysUserPrincipal(9L, "系统管理员", "SYSTEM_ADMIN");

    @BeforeEach
    void setUp() {
        feedbackMapper = mock(TicketFeedbackMapper.class);
        ticketMapper = mock(AlertTicketMapper.class);
        alertEventMapper = mock(AlertEventMapper.class);
        service = new TicketFeedbackService(feedbackMapper, ticketMapper, alertEventMapper);
    }

    @Test
    void shouldRejectUnknownRootCauseWithoutDetail() {
        AlertTicket ticket = alertTicket();
        when(ticketMapper.selectById(1L)).thenReturn(ticket);
        when(alertEventMapper.selectById(10L)).thenReturn(alert(10L));

        TicketFeedbackRequest request = request();
        request.setRootCauseCode("UNKNOWN");
        request.setRootCauseDetail(" ");

        assertThrows(IllegalArgumentException.class, () -> service.save(1L, request, operator));
        verify(feedbackMapper, never()).insert(any(TicketFeedback.class));
    }

    @Test
    void shouldRejectZeroImpactWithoutExplicitNoImpactMeaning() {
        AlertTicket ticket = alertTicket();
        when(ticketMapper.selectById(1L)).thenReturn(ticket);
        when(alertEventMapper.selectById(10L)).thenReturn(alert(10L));

        TicketFeedbackRequest request = request();
        request.setImpactLoadMw(BigDecimal.ZERO);
        request.setActionDetail("已完成检查");

        assertThrows(IllegalArgumentException.class, () -> service.save(1L, request, operator));
    }

    @Test
    void shouldRejectAlertTicketWithoutRealAlert() {
        AlertTicket ticket = alertTicket();
        when(ticketMapper.selectById(1L)).thenReturn(ticket);
        when(alertEventMapper.selectById(10L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.save(1L, request(), operator));
    }

    @Test
    void shouldSaveFeedbackIdempotentlyByTicketId() {
        AlertTicket ticket = alertTicket();
        TicketFeedback existing = new TicketFeedback();
        existing.setId(20L);
        existing.setTicketId(1L);
        existing.setOperatorUserId(2L);
        existing.setOperatorName("旧处理人");
        existing.setActionsTaken("[\"旧措施\"]");
        existing.setImpactLoadMw(BigDecimal.ONE);
        existing.setAlertClassification("TRUE_ALERT");
        existing.setRootCauseCode("EQUIPMENT");
        existing.setEffectiveness("EFFECTIVE");
        when(ticketMapper.selectById(1L)).thenReturn(ticket);
        when(alertEventMapper.selectById(10L)).thenReturn(alert(10L));
        when(feedbackMapper.selectByTicketId(1L)).thenReturn(existing);
        when(feedbackMapper.updateById(any(TicketFeedback.class))).thenReturn(1);

        var result = service.save(1L, request(), operator);

        assertEquals(1L, result.getTicketId());
        verify(feedbackMapper, never()).insert(any(TicketFeedback.class));
        verify(feedbackMapper).updateById(existing);
        assertEquals(2L, existing.getOperatorUserId());
        assertEquals("运维李四", existing.getOperatorName());
    }

    @Test
    void shouldMarkClosedFeedbackAsLegacyMissing() {
        AlertTicket ticket = alertTicket();
        ticket.setStatus("CLOSED");
        when(ticketMapper.selectById(1L)).thenReturn(ticket);
        when(feedbackMapper.selectByTicketId(1L)).thenReturn(null);

        var response = service.getFeedback(1L);

        assertEquals("LEGACY_MISSING", response.getFeedbackStatus());
    }

    @Test
    void shouldCalculateAccuracyAndReturnNullWhenNoClassificationSample() {
        AlertTicket closed = alertTicket();
        closed.setStatus("CLOSED");
        closed.setClosedAt(LocalDateTime.now());
        when(ticketMapper.selectList(any())).thenReturn(List.of(closed));
        when(feedbackMapper.selectList(any())).thenReturn(List.of());
        when(alertEventMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        var metrics = service.metrics(new TicketFeedbackMetricsFilter());

        assertEquals(1, metrics.getTotalClosedTickets());
        assertEquals(1, metrics.getFeedbackMissingCount());
        assertNull(metrics.getAlertAccuracyPercent());
    }

    private AlertTicket alertTicket() {
        AlertTicket ticket = new AlertTicket();
        ticket.setId(1L);
        ticket.setAlertId(10L);
        ticket.setSourceType("ALERT");
        ticket.setStatus("IN_PROGRESS");
        ticket.setAssigneeUserId(2L);
        ticket.setAssigneeName("运维李四");
        return ticket;
    }

    private AlertEvent alert(Long id) {
        AlertEvent event = new AlertEvent();
        event.setId(id);
        event.setLevel("RED");
        event.setCurrentValue(1200f);
        event.setThresholdValue(1100f);
        return event;
    }

    private TicketFeedbackRequest request() {
        TicketFeedbackRequest request = new TicketFeedbackRequest();
        request.setAlertClassification("TRUE_ALERT");
        request.setRootCauseCode("EQUIPMENT");
        request.setActionsTaken(List.of("检查设备状态"));
        request.setActionDetail("设备恢复，无影响");
        request.setImpactLoadMw(BigDecimal.ZERO);
        request.setEffectiveness("EFFECTIVE");
        return request;
    }
}
