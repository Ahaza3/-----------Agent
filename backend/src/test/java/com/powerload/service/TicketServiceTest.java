package com.powerload.service;

import com.powerload.dto.response.GridResponsibility;
import com.powerload.entity.*;
import com.powerload.mapper.*;
import com.powerload.security.SysUserPrincipal;
import com.powerload.websocket.PushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TicketServiceTest {

    private TicketService service;
    private AlertTicketMapper ticketMapper;
    private AlertTicketActionMapper actionMapper;
    private AlertEventMapper alertEventMapper;
    private SysUserMapper sysUserMapper;
    private GridTopologyService gridTopologyService;

    private final SysUserPrincipal dispatcher = new SysUserPrincipal(1L, "disp", "DISPATCHER");
    private final SysUserPrincipal operator = new SysUserPrincipal(2L, "oper", "OPERATOR");

    @BeforeEach
    void setUp() {
        ticketMapper = mock(AlertTicketMapper.class);
        actionMapper = mock(AlertTicketActionMapper.class);
        alertEventMapper = mock(AlertEventMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        gridTopologyService = mock(GridTopologyService.class);
        PushService push = mock(PushService.class);
        service = new TicketService(ticketMapper, actionMapper, alertEventMapper, sysUserMapper, push,
                gridTopologyService);
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
    void shouldAutoRouteFeederAlertToSubstationOperator() {
        AlertEvent e = new AlertEvent();
        e.setId(1L);
        e.setNodeId(4L);
        e.setLevel("ORANGE");
        when(alertEventMapper.selectById(1L)).thenReturn(e);
        doReturn(0L).when(ticketMapper).selectCount(any());

        SysUser eastOperator = new SysUser();
        eastOperator.setId(20L);
        eastOperator.setUsername("operator-east");
        eastOperator.setDisplayName("东部运维王五");
        eastOperator.setRole("OPERATOR");
        eastOperator.setIsActive(1);
        when(gridTopologyService.resolveResponsibility(4L)).thenReturn(responsibility(20L));
        when(sysUserMapper.selectById(20L)).thenReturn(eastOperator);
        doReturn(1).when(ticketMapper).insert((AlertTicket) any());
        doReturn(1).when(actionMapper).insert((AlertTicketAction) any());

        AlertTicket created = service.create(1L, "馈线风险", dispatcher);

        assertEquals("ASSIGNED", created.getStatus());
        assertEquals(20L, created.getAssigneeUserId());
        assertEquals("东部运维王五", created.getAssigneeName());
    }

    @Test
    void shouldCreatePrewarningTicketWithoutAlertEvent() {
        LocalDateTime forecastTime = LocalDateTime.of(2026, 7, 18, 20, 0);
        doAnswer(invocation -> {
            AlertTicket ticket = invocation.getArgument(0);
            ticket.setId(88L);
            return 1;
        }).when(ticketMapper).insert((AlertTicket) any());
        doReturn(1).when(actionMapper).insert((AlertTicketAction) any());

        AlertTicket created = service.createPrewarning("未来峰值可能越过橙色阈值", "orange",
                forecastTime, 1140f, dispatcher);

        assertTrue(created.getTicketNo().startsWith("PW-"));
        assertNull(created.getAlertId());
        assertEquals("PREWARNING", created.getSourceType());
        assertEquals("ORANGE", created.getRiskLevel());
        assertEquals(forecastTime, created.getForecastTime());
        assertEquals(1140f, created.getExpectedLoad());
        assertEquals("HIGH", created.getPriority());
        assertEquals("PENDING", created.getStatus());
        verify(alertEventMapper, never()).selectById(any());
        verify(actionMapper).insert((AlertTicketAction) any());
    }

    @Test
    void shouldAllowAssignFromPending() {
        AlertTicket t = pendingTicket();
        when(ticketMapper.selectById(1L)).thenReturn(t);
        doReturn(1).when(ticketMapper).updateById((AlertTicket) any());
        doReturn(1).when(actionMapper).insert((AlertTicketAction) any());
        SysUser op = new SysUser(); op.setId(2L); op.setUsername("oper"); op.setRole("OPERATOR"); op.setIsActive(1);
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
        SysUser op = new SysUser(); op.setId(2L); op.setUsername("oper"); op.setRole("OPERATOR"); op.setIsActive(1);
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

    private GridResponsibility responsibility(Long userId) {
        GridResponsibility value = new GridResponsibility();
        value.setAssigneeUserId(userId);
        value.setAssigneeName("东部运维王五");
        value.setSubstationCode("SUBSTATION-EAST");
        value.setSubstationName("东部变电站");
        value.setDispatchCenter(false);
        value.setRouteReason("SUBSTATION_RESPONSIBILITY");
        return value;
    }
}
