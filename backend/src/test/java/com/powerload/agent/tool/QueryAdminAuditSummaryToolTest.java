package com.powerload.agent.tool;

import com.powerload.agent.ToolResult;
import com.powerload.entity.OperationLog;
import com.powerload.entity.SysUser;
import com.powerload.mapper.OperationLogMapper;
import com.powerload.mapper.SysUserMapper;
import com.powerload.service.SystemHealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QueryAdminAuditSummaryToolTest {

    private SysUserMapper sysUserMapper;
    private OperationLogMapper operationLogMapper;
    private SystemHealthService systemHealthService;
    private QueryAdminAuditSummaryTool tool;

    @BeforeEach
    void setUp() {
        sysUserMapper = mock(SysUserMapper.class);
        operationLogMapper = mock(OperationLogMapper.class);
        systemHealthService = mock(SystemHealthService.class);
        tool = new QueryAdminAuditSummaryTool(sysUserMapper, operationLogMapper, systemHealthService);
    }

    @Test
    void shouldReturnAdminAuditSummary() {
        when(sysUserMapper.selectList(any())).thenReturn(List.of(
                user("dispatcher", "DISPATCHER", 1),
                user("operator", "OPERATOR", 1),
                user("disabled", "OPERATOR", 0)
        ));
        when(operationLogMapper.selectList(any())).thenReturn(List.of(failureLog()));
        when(systemHealthService.check()).thenReturn(Map.of("status", "UP"));

        ToolResult result = tool.execute("{}");

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("MOCK_ADMIN_AUDIT", data.get("source"));
        assertEquals(3, data.get("userCount"));
        assertEquals(1L, data.get("disabledUserCount"));
        assertEquals(Map.of("DISPATCHER", 1L, "OPERATOR", 2L), data.get("roleCounts"));
        assertEquals(Map.of("status", "UP"), data.get("health"));
        assertNotNull(data.get("recentFailureLogs"));
        verify(sysUserMapper).selectList(any());
        verify(operationLogMapper).selectList(any());
        verify(systemHealthService).check();
    }

    private SysUser user(String username, String role, int active) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setRole(role);
        user.setIsActive(active);
        return user;
    }

    private OperationLog failureLog() {
        OperationLog log = new OperationLog();
        log.setUsername("operator");
        log.setRole("OPERATOR");
        log.setModule("ticket");
        log.setAction("assign");
        log.setResult("FAILURE");
        log.setDetail("permission denied");
        log.setCreatedAt(LocalDateTime.of(2026, 7, 18, 20, 0));
        return log;
    }
}
