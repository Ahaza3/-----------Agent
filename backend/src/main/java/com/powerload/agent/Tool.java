package com.powerload.agent;

import java.util.Map;

/**
 * Agent 工具接口 — 所有工具 Bean 实现此接口，由 ToolRegistry 自动收集。
 *
 * <p>每个工具通过 Spring @Component 注册，name() 必须全局唯一。
 * parameters() 返回 OpenAI tool function 兼容的 JSON Schema。</p>
 */
public interface Tool {

    /** 工具名称，全局唯一（重复则启动失败） */
    String name();

    /** 工具描述，给 LLM 理解用途 */
    String description();

    /** 参数 JSON Schema（OpenAI tool function parameters 格式） */
    Map<String, Object> parameters();

    /** 执行工具，argumentsJson 为 LLM 传入的 JSON 参数字符串 */
    ToolResult execute(String argumentsJson);
}
