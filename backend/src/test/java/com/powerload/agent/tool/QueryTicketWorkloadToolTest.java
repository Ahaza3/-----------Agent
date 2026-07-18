package com.powerload.agent.tool;

import com.powerload.agent.ToolResult;
import com.powerload.agent.UserContextHolder;
import com.powerload.entity.AlertTicket;
import com.powerload.mapper.AlertTicketMapper;
import com.powerload.security.SysUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QueryTicketWorkloadToolTest {

    private AlertTicketMapper ticketMapper;
    private QueryTicketWorkloadTool tool;

    @BeforeEach
    void setUp() {
        ticketMapper = mock(AlertTicketMapper.class);
        tool = new QueryTicketWorkloadTool(ticketMapper);
        UserContextHolder.set(new SysUserPrincipal(2L, "operator", "OPERATOR"));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void shouldReturnOperatorTicketWorkload() {
        when(ticketMapper.selectCount(any()))
                .thenReturn(3L)
                .thenReturn(2L)
                .thenReturn(1L)
                .thenReturn(1L);
        when(ticketMapper.selectList(any())).thenReturn(List.of(ticket()));

        ToolResult result = tool.execute("{}");

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("MOCK_TICKET_WORKLOAD", data.get("source"));
        assertEquals(3L, data.get("pendingCount"));
        assertEquals(1L, data.get("assignedToMeCount"));
        assertEquals(2L, data.get("inProgressMineCount"));
        assertEquals(1L, data.get("openPrewarningCount"));
        assertNotNull(data.get("recentOpenTickets"));
        verify(ticketMapper, times(4)).selectCount(any());
        verify(ticketMapper).selectList(any());
    }

    private AlertTicket ticket() {
        AlertTicket ticket = new AlertTicket();
        ticket.setTicketNo("PW-20260718200000-ABCD");
        ticket.setSourceType("PREWARNING");
        ticket.setPriority("HIGH");
        ticket.setStatus("PENDING");
        ticket.setSummary("未来峰值风险");
        ticket.setForecastTime(LocalDateTime.of(2026, 7, 18, 20, 0));
        ticket.setExpectedLoad(1140f);
        return ticket;
    }
}
