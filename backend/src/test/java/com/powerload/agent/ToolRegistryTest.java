package com.powerload.agent;

import com.powerload.audit.AgentToolAuditService;
import com.powerload.entity.OperationLog;
import com.powerload.mapper.OperationLogMapper;
import com.powerload.security.SysUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolRegistryTest {

    private OperationLogMapper operationLogMapper;
    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        operationLogMapper = mock(OperationLogMapper.class);
        registry = new ToolRegistry(List.of(
                tool("query_load", Set.of("DISPATCHER", "OPERATOR", "SYSTEM_ADMIN")),
                tool("query_admin_audit_summary", Set.of("SYSTEM_ADMIN")),
                tool("query_system_health", Set.of("OPERATOR", "SYSTEM_ADMIN")),
                tool("query_dispatch_risk_brief", Set.of("DISPATCHER", "SYSTEM_ADMIN")),
                tool("query_alert_judgement", Set.of("DISPATCHER", "SYSTEM_ADMIN")),
                tool("query_ticket_workload", Set.of("OPERATOR", "SYSTEM_ADMIN")),
                tool("generate_ticket_report", Set.of("OPERATOR", "SYSTEM_ADMIN"))
        ), new AgentToolAuditService(operationLogMapper));
    }

    @Test
    void shouldFilterToolDefinitionsForEachAuthenticatedRole() {
        assertEquals(Set.of("query_load", "query_dispatch_risk_brief", "query_alert_judgement"),
                definitionNames(dispatcher()));
        assertEquals(Set.of("query_load", "query_system_health", "query_ticket_workload", "generate_ticket_report"),
                definitionNames(operator()));
        assertEquals(Set.of("query_load", "query_admin_audit_summary", "query_system_health",
                "query_dispatch_risk_brief", "query_alert_judgement", "query_ticket_workload",
                "generate_ticket_report"), definitionNames(admin()));
    }

    @Test
    void shouldDenyEmptyAndUnknownIdentity() {
        AtomicInteger calls = new AtomicInteger();
        ToolRegistry isolated = new ToolRegistry(List.of(tool("admin", Set.of("SYSTEM_ADMIN"), calls)),
                new AgentToolAuditService(operationLogMapper));

        assertTrue(isolated.getToolDefinitions(null).isEmpty());
        assertFalse(isolated.execute(null, "admin", "{}").isSuccess());
        assertTrue(isolated.getToolDefinitions(new SysUserPrincipal(9L, "unknown", "VIEWER")).isEmpty());
        assertFalse(isolated.execute(new SysUserPrincipal(9L, "unknown", "VIEWER"), "admin", "{}").isSuccess());
        assertEquals(0, calls.get());
    }

    @Test
    void shouldRejectForgedUnissuedToolCallWithoutExecutingTool() {
        AtomicInteger calls = new AtomicInteger();
        ToolRegistry isolated = new ToolRegistry(List.of(tool("query_admin_audit_summary", Set.of("SYSTEM_ADMIN"), calls)),
                new AgentToolAuditService(operationLogMapper));

        ToolResult result = isolated.execute(dispatcher(), "query_admin_audit_summary", "{\"role\":\"SYSTEM_ADMIN\"}");
        ToolResult operatorResult = isolated.execute(operator(), "query_admin_audit_summary", "{}");

        assertFalse(result.isSuccess());
        assertFalse(operatorResult.isSuccess());
        assertEquals(0, calls.get());
        assertTrue(result.getMessage().contains("无权"));
        OperationLog audit = latestAudit();
        assertEquals("DENIED", audit.getResult());
        assertEquals("ROLE_NOT_ALLOWED", audit.getDetail());
        assertFalse(audit.getDetail().contains("SYSTEM_ADMIN"));
    }

    @Test
    void shouldAuditSuccessDeniedAndFailureWithoutSensitiveArguments() {
        AtomicInteger successCalls = new AtomicInteger();
        ToolRegistry isolated = new ToolRegistry(List.of(
                tool("general", Set.of("DISPATCHER", "OPERATOR", "SYSTEM_ADMIN"), successCalls),
                crashingTool()
        ), new AgentToolAuditService(operationLogMapper));

        assertTrue(isolated.execute(dispatcher(), "general", "{\"apiKey\":\"secret-value\"}").isSuccess());
        assertFalse(isolated.execute(dispatcher(), "crash", "{\"jwt\":\"secret-value\"}").isSuccess());
        assertFalse(isolated.execute(dispatcher(), "missing", "{\"token\":\"secret-value\"}").isSuccess());

        assertEquals(1, successCalls.get());
        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper, times(3)).insert(captor.capture());
        List<OperationLog> logs = captor.getAllValues();
        assertEquals(List.of("SUCCESS", "FAILURE", "FAILURE"),
                logs.stream().map(OperationLog::getResult).toList());
        assertEquals(1L, logs.get(0).getUserId());
        assertEquals("dispatcher", logs.get(0).getUsername());
        assertEquals("DISPATCHER", logs.get(0).getRole());
        assertEquals("general", logs.get(0).getAction());
        assertEquals("AGENT_TOOL", logs.get(0).getModule());
        assertEquals("TOOL_EXCEPTION", logs.get(1).getDetail());
        assertEquals("UNKNOWN_TOOL", logs.get(2).getDetail());
        assertTrue(logs.stream().map(OperationLog::getDetail).noneMatch(detail -> detail.contains("secret-value")));
        assertTrue(logs.stream().allMatch(log -> log.getDurationMs() >= 0));
    }

    @Test
    void shouldRejectToolsWithoutExplicitNonEmptyKnownRoles() {
        assertThrows(IllegalStateException.class, () -> new ToolRegistry(
                List.of(tool("empty", Set.of())), new AgentToolAuditService(operationLogMapper)));
        assertThrows(IllegalStateException.class, () -> new ToolRegistry(
                List.of(tool("unknown-role", Set.of("VIEWER"))), new AgentToolAuditService(operationLogMapper)));
    }

    @Test
    void shouldAuditFailureWhenAuditStorageFailsWithoutChangingAuthorization() {
        doThrow(new RuntimeException("database unavailable")).when(operationLogMapper).insert(any(OperationLog.class));
        ToolResult result = registry.execute(dispatcher(), "query_admin_audit_summary", "{}");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("无权"));
    }

    private Set<String> definitionNames(SysUserPrincipal principal) {
        return registry.getToolDefinitions(principal).stream()
                .map(def -> (Map<String, Object>) def.get("function"))
                .map(function -> (String) function.get("name"))
                .collect(Collectors.toSet());
    }

    private OperationLog latestAudit() {
        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper, atLeastOnce()).insert(captor.capture());
        return captor.getValue();
    }

    private static SysUserPrincipal dispatcher() {
        return new SysUserPrincipal(1L, "dispatcher", "DISPATCHER");
    }

    private static SysUserPrincipal operator() {
        return new SysUserPrincipal(2L, "operator", "OPERATOR");
    }

    private static SysUserPrincipal admin() {
        return new SysUserPrincipal(3L, "admin", "SYSTEM_ADMIN");
    }

    private static Tool tool(String name, Set<String> roles) {
        return tool(name, roles, new AtomicInteger());
    }

    private static Tool tool(String name, Set<String> roles, AtomicInteger calls) {
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return "description of " + name; }
            @Override public Set<String> allowedRoles() { return roles; }
            @Override public Map<String, Object> parameters() { return Map.of("type", "object"); }
            @Override public ToolResult execute(String args) {
                calls.incrementAndGet();
                return ToolResult.ok("ok", null);
            }
        };
    }

    private static Tool crashingTool() {
        return new Tool() {
            @Override public String name() { return "crash"; }
            @Override public String description() { return "crash"; }
            @Override public Set<String> allowedRoles() { return Set.of("DISPATCHER"); }
            @Override public Map<String, Object> parameters() { return Map.of(); }
            @Override public ToolResult execute(String args) { throw new RuntimeException("apiKey=secret-value"); }
        };
    }
}
