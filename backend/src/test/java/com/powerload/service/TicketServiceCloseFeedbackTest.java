package com.powerload.service;

import com.powerload.entity.AlertTicket;
import com.powerload.entity.TicketFeedback;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.mapper.AlertTicketActionMapper;
import com.powerload.mapper.AlertTicketMapper;
import com.powerload.mapper.SysUserMapper;
import com.powerload.security.SysUserPrincipal;
import com.powerload.websocket.PushService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TicketServiceCloseFeedbackTest {

    @Test
    void shouldRejectCloseWhenFeedbackIsIncomplete() {
        AlertTicketMapper ticketMapper = mock(AlertTicketMapper.class);
        AlertTicketActionMapper actionMapper = mock(AlertTicketActionMapper.class);
        AlertEventMapper alertEventMapper = mock(AlertEventMapper.class);
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        PushService pushService = mock(PushService.class);
        TicketFeedbackService feedbackService = mock(TicketFeedbackService.class);
        TicketService service = new TicketService(ticketMapper, actionMapper, alertEventMapper,
                sysUserMapper, pushService, null, feedbackService);

        AlertTicket ticket = resolvedTicket();
        when(ticketMapper.selectById(1L)).thenReturn(ticket);
        doThrow(new IllegalStateException("关闭工单前必须提交完整结构化反馈"))
                .when(feedbackService).requireCompleteForClose(ticket, dispatcher());

        assertThrows(IllegalStateException.class, () -> service.close(1L, dispatcher()));
        verify(ticketMapper, never()).updateById(any(AlertTicket.class));
        verify(actionMapper, never()).insert(any(com.powerload.entity.AlertTicketAction.class));
    }

    @Test
    void shouldReviewFeedbackAndCloseInOneFlow() {
        AlertTicketMapper ticketMapper = mock(AlertTicketMapper.class);
        AlertTicketActionMapper actionMapper = mock(AlertTicketActionMapper.class);
        AlertEventMapper alertEventMapper = mock(AlertEventMapper.class);
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        PushService pushService = mock(PushService.class);
        TicketFeedbackService feedbackService = mock(TicketFeedbackService.class);
        TicketService service = new TicketService(ticketMapper, actionMapper, alertEventMapper,
                sysUserMapper, pushService, null, feedbackService);

        AlertTicket ticket = resolvedTicket();
        TicketFeedback feedback = new TicketFeedback();
        feedback.setTicketId(1L);
        feedback.setOperatorUserId(2L);
        when(ticketMapper.selectById(1L)).thenReturn(ticket);
        when(feedbackService.requireCompleteForClose(ticket, dispatcher())).thenReturn(feedback);
        when(ticketMapper.updateById(ticket)).thenReturn(1);
        when(actionMapper.insert(any(com.powerload.entity.AlertTicketAction.class))).thenReturn(1);

        AlertTicket closed = service.close(1L, dispatcher());

        assertEquals("CLOSED", closed.getStatus());
        assertNotNull(closed.getClosedAt());
        verify(feedbackService).markReviewed(feedback, dispatcher());
        verify(actionMapper).insert(any(com.powerload.entity.AlertTicketAction.class));
    }

    @Test
    void shouldMakeRepeatedCloseIdempotent() {
        AlertTicketMapper ticketMapper = mock(AlertTicketMapper.class);
        TicketService service = new TicketService(ticketMapper, mock(AlertTicketActionMapper.class),
                mock(AlertEventMapper.class), mock(SysUserMapper.class), mock(PushService.class),
                null, mock(TicketFeedbackService.class));
        AlertTicket closed = resolvedTicket();
        closed.setStatus("CLOSED");
        when(ticketMapper.selectById(1L)).thenReturn(closed);

        assertSame(closed, service.close(1L, dispatcher()));
        verify(ticketMapper, never()).updateById(any(AlertTicket.class));
    }

    private AlertTicket resolvedTicket() {
        AlertTicket ticket = new AlertTicket();
        ticket.setId(1L);
        ticket.setStatus("RESOLVED");
        ticket.setAssigneeUserId(2L);
        ticket.setVersion(1);
        return ticket;
    }

    private SysUserPrincipal dispatcher() {
        return new SysUserPrincipal(1L, "调度张三", "DISPATCHER");
    }
}
