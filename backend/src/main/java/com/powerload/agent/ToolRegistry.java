package com.powerload.agent;

import com.powerload.audit.AgentToolAuditService;
import com.powerload.security.SysUserPrincipal;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心 — 通过 Spring DI 收集所有 Tool Bean。
 *
 * <p>启动时检查工具名唯一性，重复则抛出 IllegalStateException 阻止启动。
 * 运行时 dispatch 已知工具或返回结构化错误。</p>
 */
@Slf4j
@Component
public class ToolRegistry {

    private static final Set<String> AUTHENTICATED_ROLES = Set.of("DISPATCHER", "OPERATOR", "SYSTEM_ADMIN");
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final AgentToolAuditService auditService;

    /** Spring 自动注入所有 Tool 实现 */
    public ToolRegistry(List<Tool> toolList, AgentToolAuditService auditService) {
        this.auditService = auditService;
        for (Tool tool : toolList) {
            String name = tool.name();
            if (tools.containsKey(name)) {
                throw new IllegalStateException(
                        "工具名重复: " + name + " — 已注册 " + tools.get(name).getClass().getSimpleName()
                        + "，冲突 " + tool.getClass().getSimpleName());
            }
            validateAllowedRoles(tool);
            tools.put(name, tool);
            log.info("注册工具: {} ({})", name, tool.getClass().getSimpleName());
        }
    }

    @PostConstruct
    void logSummary() {
        log.info("ToolRegistry 就绪，共 {} 个工具: {}", tools.size(), tools.keySet());
    }

    /** Returns only the OpenAI tool definitions authorized for the trusted principal. */
    public List<Map<String, Object>> getToolDefinitions(SysUserPrincipal user) {
        List<Map<String, Object>> defs = new ArrayList<>();
        for (Tool tool : tools.values().stream().sorted(Comparator.comparing(Tool::name)).toList()) {
            if (!isAuthorized(user, tool)) {
                continue;
            }
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("type", "function");
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("name", tool.name());
            func.put("description", tool.description());
            func.put("parameters", tool.parameters());
            def.put("function", func);
            defs.add(def);
        }
        return defs;
    }

    /** Executes a tool only after validating the same trusted principal used for definition filtering. */
    public ToolResult execute(SysUserPrincipal user, String toolName, String argumentsJson) {
        long start = System.currentTimeMillis();
        String auditToolName = sanitizeToolName(toolName);
        if (toolName == null || toolName.isBlank()) {
            audit(user, auditToolName, "FAILURE", start, "MISSING_TOOL_NAME");
            return ToolResult.fail("工具名不能为空");
        }
        Tool tool = tools.get(toolName);
        if (tool == null) {
            log.warn("未知 Agent 工具调用: {}", auditToolName);
            audit(user, auditToolName, "FAILURE", start, "UNKNOWN_TOOL");
            return ToolResult.fail("请求的工具不可用");
        }
        if (!isAuthorized(user, tool)) {
            log.warn("拒绝未授权 Agent 工具调用: userId={}, tool={}",
                    user != null ? user.getUserId() : null, auditToolName);
            audit(user, auditToolName, "DENIED", start, authorizationFailureReason(user));
            return ToolResult.fail("当前身份无权调用该工具");
        }
        log.debug("执行 Agent 工具: {}", auditToolName);
        try {
            ToolResult result = tool.execute(argumentsJson);
            audit(user, auditToolName, result.isSuccess() ? "SUCCESS" : "FAILURE", start,
                    result.isSuccess() ? "" : "TOOL_REPORTED_FAILURE");
            return result;
        } catch (Exception e) {
            log.error("Agent 工具执行异常: tool={}, exceptionType={}", auditToolName,
                    e.getClass().getSimpleName());
            audit(user, auditToolName, "FAILURE", start, "TOOL_EXCEPTION");
            return ToolResult.fail("工具执行失败，请稍后重试");
        }
    }

    private void validateAllowedRoles(Tool tool) {
        Set<String> roles = tool.allowedRoles();
        if (roles == null || roles.isEmpty() || !AUTHENTICATED_ROLES.containsAll(roles)) {
            throw new IllegalStateException("工具 " + tool.name() + " 必须声明非空且有效的 allowedRoles");
        }
    }

    private boolean isAuthorized(SysUserPrincipal user, Tool tool) {
        return user != null
                && user.getUserId() != null
                && user.getRole() != null
                && AUTHENTICATED_ROLES.contains(user.getRole())
                && tool.allowedRoles().contains(user.getRole());
    }

    private String authorizationFailureReason(SysUserPrincipal user) {
        if (user == null || user.getUserId() == null || user.getRole() == null || user.getRole().isBlank()) {
            return "MISSING_PRINCIPAL";
        }
        return AUTHENTICATED_ROLES.contains(user.getRole()) ? "ROLE_NOT_ALLOWED" : "UNKNOWN_ROLE";
    }

    private void audit(SysUserPrincipal user, String toolName, String result, long start, String reason) {
        auditService.record(user, toolName, result, System.currentTimeMillis() - start, reason);
    }

    private String sanitizeToolName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "(missing)";
        }
        String cleaned = toolName.replaceAll("[^A-Za-z0-9_.-]", "_");
        return cleaned.length() <= 100 ? cleaned : cleaned.substring(0, 100);
    }

    /** 已注册工具数量（测试用） */
    int size() {
        return tools.size();
    }

    /** 是否包含指定工具（测试用） */
    boolean contains(String name) {
        return tools.containsKey(name);
    }
}
