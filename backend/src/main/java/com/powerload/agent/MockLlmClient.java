package com.powerload.agent;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Mock LLM 客户端 — 开发和测试用，返回可配置的预设回复。
 *
 * <p>LLM_API_KEY 为空时自动使用此实现。通过构造函数或 setter 预设
 * 每次 chat() 的返回值，支持模拟 tool_calls 和多轮对话。</p>
 */
@Slf4j
public class MockLlmClient implements LlmClient {

    private AgentMessage nextResponse;
    private int callCount = 0;

    public MockLlmClient() {
        this.nextResponse = AgentMessage.assistant("这是 Mock LLM 的回复。LLM API Key 尚未配置。");
    }

    /** 预设下一次 chat() 的返回值 */
    public void setNextResponse(AgentMessage response) {
        this.nextResponse = response;
    }

    public int getCallCount() {
        return callCount;
    }

    @Override
    public AgentMessage chat(List<AgentMessage> messages, List<Map<String, Object>> tools)
            throws LlmException {
        callCount++;
        log.debug("MockLlmClient 第 {} 次调用", callCount);
        if (nextResponse == null) {
            return AgentMessage.assistant("Mock 回复为空。");
        }
        return nextResponse;
    }
}
