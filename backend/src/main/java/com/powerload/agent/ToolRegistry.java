package com.powerload.agent;

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

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    /** Spring 自动注入所有 Tool 实现 */
    public ToolRegistry(List<Tool> toolList) {
        for (Tool tool : toolList) {
            String name = tool.name();
            if (tools.containsKey(name)) {
                throw new IllegalStateException(
                        "工具名重复: " + name + " — 已注册 " + tools.get(name).getClass().getSimpleName()
                        + "，冲突 " + tool.getClass().getSimpleName());
            }
            tools.put(name, tool);
            log.info("注册工具: {} ({})", name, tool.getClass().getSimpleName());
        }
    }

    @PostConstruct
    void logSummary() {
        log.info("ToolRegistry 就绪，共 {} 个工具: {}", tools.size(), tools.keySet());
    }

    /** 获取所有工具的 OpenAI tool definition 列表 */
    public List<Map<String, Object>> getToolDefinitions() {
        List<Map<String, Object>> defs = new ArrayList<>();
        for (Tool tool : tools.values()) {
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

    /** 执行指定工具 */
    public ToolResult execute(String toolName, String argumentsJson) {
        if (toolName == null) {
            return ToolResult.fail("工具名不能为空，可用工具: " + tools.keySet());
        }
        Tool tool = tools.get(toolName);
        if (tool == null) {
            String msg = "未知工具: " + toolName + "，可用工具: " + tools.keySet();
            log.warn(msg);
            return ToolResult.fail(msg);
        }
        log.debug("执行工具: {} args={}", toolName, argumentsJson);
        try {
            return tool.execute(argumentsJson);
        } catch (Exception e) {
            log.error("工具 {} 执行异常", toolName, e);
            return ToolResult.fail("工具 " + toolName + " 执行失败: " + e.getMessage());
        }
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
