package com.powerload.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agent 消息 — 用于构建 LLM 请求的 messages 数组，以及保存到 Conversation。
 *
 * <p>兼容 OpenAI messages 格式：role + content + tool_calls + tool_call_id。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage {

    private String role;
    private String content;
    private String toolCallId;
    private String toolName;
    private List<Map<String, Object>> toolCalls;

    public static AgentMessage system(String content) {
        AgentMessage m = new AgentMessage();
        m.role = "system";
        m.content = content;
        return m;
    }

    public static AgentMessage user(String content) {
        AgentMessage m = new AgentMessage();
        m.role = "user";
        m.content = content;
        return m;
    }

    public static AgentMessage assistant(String content) {
        AgentMessage m = new AgentMessage();
        m.role = "assistant";
        m.content = content;
        return m;
    }

    public static AgentMessage assistantWithToolCalls(List<Map<String, Object>> toolCalls) {
        AgentMessage m = new AgentMessage();
        m.role = "assistant";
        m.content = null;
        m.toolCalls = toolCalls;
        return m;
    }

    public static AgentMessage tool(String toolCallId, String toolName, String content) {
        AgentMessage m = new AgentMessage();
        m.role = "tool";
        m.toolCallId = toolCallId;
        m.toolName = toolName;
        m.content = content;
        return m;
    }
}
