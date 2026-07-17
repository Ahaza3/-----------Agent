package com.powerload.service;

import com.powerload.dto.response.AssigneeInfo;
import com.powerload.entity.AlertTicket;
import com.powerload.entity.SysUser;
import com.powerload.mapper.*;
import com.powerload.security.SysUserPrincipal;
import com.powerload.websocket.PushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceAssignTest {

    @Mock private AlertTicketMapper ticketMapper;
    @Mock private AlertTicketActionMapper actionMapper;
    @Mock private AlertEventMapper alertEventMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private PushService pushService;

    @InjectMocks
    private TicketService ticketService;

    private SysUser operator;
    private SysUser dispatcher;
    private SysUser inactiveOperator;

    @BeforeEach
    void setUp() {
        operator = new SysUser();
        operator.setId(2L);
        operator.setUsername("operator");
        operator.setDisplayName("运维李四");
        operator.setRole("OPERATOR");
        operator.setIsActive(1);

        dispatcher = new SysUser();
        dispatcher.setId(1L);
        dispatcher.setUsername("dispatcher");
        dispatcher.setRole("DISPATCHER");
        dispatcher.setIsActive(1);

        inactiveOperator = new SysUser();
        inactiveOperator.setId(3L);
        inactiveOperator.setUsername("operator_old");
        inactiveOperator.setRole("OPERATOR");
        inactiveOperator.setIsActive(0);
    }

    @Test
    void listAssigneesShouldReturnOnlyActiveOperators() {
        // Mock 只返回活跃 OPERATOR（MyBatis-Plus 的 LambdaQueryWrapper 在 mock 中不生效）
        when(sysUserMapper.selectList(any()))
            .thenReturn(List.of(operator));

        List<AssigneeInfo> assignees = ticketService.listAssignees();

        assertEquals(1, assignees.size());
        assertEquals(2L, assignees.get(0).getId());
        assertEquals("运维李四", assignees.get(0).getDisplayName());
        assertTrue(assignees.get(0).isActive());
    }

    @Test
    void listAssigneesShouldReturnEmptyWhenNoOperators() {
        when(sysUserMapper.selectList(any())).thenReturn(List.of());

        List<AssigneeInfo> assignees = ticketService.listAssignees();

        assertTrue(assignees.isEmpty());
    }

    @Test
    void assignShouldRejectNonOperatorAssignee() {
        AlertTicket ticket = new AlertTicket();
        ticket.setId(10L);
        ticket.setStatus("PENDING");
        ticket.setCreatedBy(1L);

        SysUser nonOp = new SysUser();
        nonOp.setId(4L);
        nonOp.setRole("DISPATCHER");
        nonOp.setIsActive(1);

        when(ticketMapper.selectById(10L)).thenReturn(ticket);
        when(sysUserMapper.selectById(4L)).thenReturn(nonOp);

        SysUserPrincipal principal = new SysUserPrincipal(1L, "dispatcher", "DISPATCHER");

        assertThrows(IllegalArgumentException.class, () -> {
            ticketService.assign(10L, 4L, principal);
        }, "只能指派给运维人员");
    }

    @Test
    void assignShouldRejectInactiveOperator() {
        AlertTicket ticket = new AlertTicket();
        ticket.setId(10L);
        ticket.setStatus("PENDING");
        ticket.setCreatedBy(1L);

        when(ticketMapper.selectById(10L)).thenReturn(ticket);
        when(sysUserMapper.selectById(3L)).thenReturn(inactiveOperator);

        SysUserPrincipal principal = new SysUserPrincipal(1L, "dispatcher", "DISPATCHER");

        assertThrows(IllegalArgumentException.class, () -> {
            ticketService.assign(10L, 3L, principal);
        }, "该运维人员已被禁用");
    }
}
