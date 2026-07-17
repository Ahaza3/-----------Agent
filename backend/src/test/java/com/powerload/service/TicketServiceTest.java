package com.powerload.service;

import com.powerload.entity.*;
import com.powerload.mapper.*;
import com.powerload.security.SysUserPrincipal;
import com.powerload.websocket.PushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TicketServiceTest {

    private TicketService service;
    private AlertTicketMapper ticketMapper;
    private AlertTicketActionMapper actionMapper;
    private AlertEventMapper alertEventMapper;
    private SysUserMapper sysUserMapper;

    private final SysUserPrincipal dispatcher = new SysUserPrincipal(1L, "disp", "DISPATCHER");
    private final SysUserPrincipal operator = new SysUserPrincipal(2L, "oper", "OPERATOR");

    @BeforeEach
    void setUp() {
        ticketMapper = mock(AlertTicketMapper.class);
        actionMapper = mock(AlertTicketActionMapper.class);
        alertEventMapper = mock(AlertEventMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        PushService push = mock(PushService.class);
        service = new TicketService(ticketMapper, actionMapper, alertEventMapper, sysUserMapper, push);
    }

    @Test
    void shouldNotCreateDuplicateTicket() {
        AlertEvent e = new AlertEvent(); e.setId(1L);
        when(alertEventMapper.selectById(1L)).thenReturn(e);
        doReturn(1L).when(ticketMapper).selectCount(any());
        assertThrows(IllegalStateException.class, () -> service.create(1L, "t", dispatcher));
    }

    @Test
    void shouldMapRedToUrgent() {
        AlertEvent e = new AlertEvent(); e.setId(1L); e.setLevel("RED");
        when(alertEventMapper.selectById(1L)).thenReturn(e);
        doReturn(0L).when(ticketMapper).selectCount(any());
        doReturn(1).when(ticketMapper).insert((AlertTicket) any());
        doReturn(1).when(actionMapper).insert((AlertTicketAction) any());
        assertEquals("URGENT", service.create(1L, "s", dispatcher).getPriority());
    }

    @Test
    void shouldAllowAssignFromPending() {
        AlertTicket t = pendingTicket();
        when(ticketMapper.selectById(1L)).thenReturn(t);
        doReturn(1).when(ticketMapper).updateById((AlertTicket) any());
        doReturn(1).when(actionMapper).insert((AlertTicketAction) any());
        SysUser op = new SysUser(); op.setId(2L); op.setUsername("oper");
        when(sysUserMapper.selectById(2L)).thenReturn(op);

        var r = service.assign(1L, 2L, dispatcher);
        assertEquals("ASSIGNED", r.getStatus());
    }

    @Test
    void shouldRejectResolvedToAssigned() {
        AlertTicket t = pendingTicket(); t.setStatus("RESOLVED");
        when(ticketMapper.selectById(1L)).thenReturn(t);
        assertThrows(IllegalStateException.class, () -> service.assign(1L, 2L, dispatcher));
    }

    @Test
    void shouldRejectClaimWhenAssignedToOther() {
        AlertTicket t = pendingTicket(); t.setStatus("ASSIGNED"); t.setAssigneeUserId(99L);
        when(ticketMapper.selectById(1L)).thenReturn(t);
        assertThrows(IllegalStateException.class, () -> service.claim(1L, operator));
    }

    @Test
    void shouldRejectResolveWithoutText() {
        AlertTicket t = pendingTicket(); t.setStatus("IN_PROGRESS"); t.setAssigneeUserId(2L);
        when(ticketMapper.selectById(1L)).thenReturn(t);
        assertThrows(IllegalArgumentException.class, () -> service.resolve(1L, "  ", operator));
    }

    @Test
    void shouldSyncAlertResolvedAtOnResolve() {
        AlertTicket t = pendingTicket(); t.setStatus("IN_PROGRESS"); t.setAssigneeUserId(2L); t.setAlertId(10L);
        when(ticketMapper.selectById(1L)).thenReturn(t);
        doReturn(1).when(ticketMapper).updateById((AlertTicket) any());
        doReturn(1).when(actionMapper).insert((AlertTicketAction) any());
        AlertEvent alert = new AlertEvent(); alert.setId(10L);
        when(alertEventMapper.selectById(10L)).thenReturn(alert);
        doReturn(1).when(alertEventMapper).updateById((AlertEvent) any());

        service.resolve(1L, "处理完成", operator);
        assertNotNull(alert.getResolvedAt());
    }

    @Test
    void shouldRejectClosedTicket() {
        AlertTicket t = pendingTicket(); t.setStatus("CLOSED");
        when(ticketMapper.selectById(1L)).thenReturn(t);
        assertThrows(IllegalStateException.class, () -> service.assign(1L, 2L, dispatcher));
    }

    @Test
    void shouldRecordActionOnTransition() {
        AlertTicket t = pendingTicket();
        when(ticketMapper.selectById(1L)).thenReturn(t);
        doReturn(1).when(ticketMapper).updateById((AlertTicket) any());
        doReturn(1).when(actionMapper).insert((AlertTicketAction) any());
        SysUser op = new SysUser(); op.setId(2L); op.setUsername("oper");
        when(sysUserMapper.selectById(2L)).thenReturn(op);

        service.assign(1L, 2L, dispatcher);
        verify(actionMapper).insert((AlertTicketAction) any());
    }

    private AlertTicket pendingTicket() {
        AlertTicket t = new AlertTicket();
        t.setId(1L); t.setTicketNo("TK-1"); t.setAlertId(10L);
        t.setStatus("PENDING"); t.setPriority("HIGH");
        t.setCreatedBy(1L); t.setCreatedByName("disp");
        return t;
    }
}
