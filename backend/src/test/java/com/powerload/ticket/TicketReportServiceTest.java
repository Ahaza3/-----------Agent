package com.powerload.ticket;

import com.powerload.entity.AlertEvent;
import com.powerload.entity.AlertTicket;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.mapper.AlertTicketActionMapper;
import com.powerload.mapper.AlertTicketMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketReportServiceTest {

    @Mock private AlertTicketMapper ticketMapper;
    @Mock private AlertTicketActionMapper actionMapper;
    @Mock private AlertEventMapper alertEventMapper;
    @InjectMocks private TicketReportService service;

    @Test
    void shouldGenerateReportForAlertTicket() {
        AlertTicket ticket = new AlertTicket();
        ticket.setId(1L); ticket.setTicketNo("TK-20260101000000");
        ticket.setPriority("URGENT"); ticket.setStatus("IN_PROGRESS");
        ticket.setAlertId(10L);
        when(ticketMapper.selectById(1L)).thenReturn(ticket);

        AlertEvent alert = new AlertEvent();
        alert.setId(10L); alert.setLevel("RED");
        alert.setCurrentValue(1200f); alert.setThresholdValue(1100f);
        when(alertEventMapper.selectById(10L)).thenReturn(alert);

        Map<String, Object> r = service.generateReport(1L, "已处理完成");
        String report = (String) r.get("report");
        assertNotNull(report);
        assertTrue(report.contains("TK-20260101000000"));
        assertTrue(report.contains("已处理完成"));
        assertEquals("RULE_BASED_AGENT", r.get("source"));
    }

    @Test
    void shouldNotCrashWhenOperatorNoteEmpty() {
        AlertTicket ticket = new AlertTicket();
        ticket.setId(2L); ticket.setTicketNo("TK-20260102000000");
        ticket.setPriority("NORMAL"); ticket.setStatus("PENDING");
        ticket.setAlertId(null);
        when(ticketMapper.selectById(2L)).thenReturn(ticket);

        Map<String, Object> r = service.generateReport(2L, null);
        String report = (String) r.get("report");
        assertNotNull(report);
        assertTrue(report.contains("待补充"));
    }

    @Test
    void shouldGenerateReportForPrewarningTicket() {
        AlertTicket ticket = new AlertTicket();
        ticket.setId(3L); ticket.setTicketNo("TK-PW-20260103000000");
        ticket.setPriority("HIGH"); ticket.setStatus("PENDING");
        ticket.setAlertId(null); // PREWARNING
        when(ticketMapper.selectById(3L)).thenReturn(ticket);

        Map<String, Object> r = service.generateReport(3L, "预防性检查");
        String report = (String) r.get("report");
        assertNotNull(report);
        assertTrue(report.contains("预测预警"));
    }

    @Test
    void shouldThrowWhenTicketNotFound() {
        when(ticketMapper.selectById(999L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.generateReport(999L, ""));
    }
}
