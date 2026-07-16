package com.powerload.agent;

import java.util.List;
import java.util.Map;

/**
 * LLM 调用客户端接口 — AgentCore 依赖此接口，不直接依赖 HTTP。
 *
 * <p>实现类：MockLlmClient（测试）、OpenAiCompatibleLlmClient（生产）。</p>
 */
public interface LlmClient {

    /**
     * 发送消息列表到 LLM，返回 assistant 消息。
     *
     * @param messages 完整消息列表（system + user + assistant + tool）
     * @param tools    OpenAI tool definitions（null 时不传 tools）
     * @return assistant 消息，包含 content 或 tool_calls
     * @throws LlmException 调用失败
     */
    AgentMessage chat(List<AgentMessage> messages, List<Map<String, Object>> tools) throws LlmException;
}
