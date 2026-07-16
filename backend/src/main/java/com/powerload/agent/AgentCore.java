package com.powerload.agent;

import cn.hutool.json.JSONUtil;
import com.powerload.agent.prompt.AgentPrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 核心编排 — 负责 LLM 调用循环、工具调度和结果聚合。
 *
 * <p>约 150 行，不包含 HTTP、数据库、工具实现细节。</p>
 */
@Slf4j
@Component
public class AgentCore {

    private static final int MAX_TOOL_ROUNDS = 3;
    private static final int MAX_TOOLS_PER_ROUND = 3;
    private static final int MAX_USER_MESSAGE_LENGTH = 2000;
    private static final int MAX_TOOL_RESULT_JSON_LENGTH = 4000;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;

    public AgentCore(LlmClient llmClient, ToolRegistry toolRegistry) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 执行 Agent 对话核心循环。
     *
     * @param userMessage  用户输入（已截断）
     * @param history      历史消息（system + 之前对话）
     * @param callback     事件回调（thinking / text / chart / debug）
     * @return 最终 assistant 回答
     */
    public AgentResponse run(String userMessage, List<AgentMessage> history, AgentCallback callback) {
        if (userMessage == null || userMessage.isBlank()) {
            return AgentResponse.error("消息不能为空");
        }
        if (userMessage.length() > MAX_USER_MESSAGE_LENGTH) {
            userMessage = userMessage.substring(0, MAX_USER_MESSAGE_LENGTH);
        }

        List<AgentMessage> messages = new ArrayList<>();
        messages.add(AgentMessage.system(AgentPrompt.systemPrompt()));
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(AgentMessage.user(userMessage));

        List<Map<String, Object>> toolDefs = toolRegistry.getToolDefinitions();

        try {
            return runLoop(messages, toolDefs, 0, callback);
        } catch (LlmException e) {
            log.error("Agent LLM 异常", e);
            return AgentResponse.error("LLM 调用失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("Agent 未知异常", e);
            return AgentResponse.error("Agent 处理异常: " + e.getMessage());
        }
    }

    private AgentResponse runLoop(List<AgentMessage> messages,
                                   List<Map<String, Object>> toolDefs,
                                   int round,
                                   AgentCallback callback) throws LlmException {
        if (round >= MAX_TOOL_ROUNDS) {
            return AgentResponse.error("达到最大工具调用轮次限制（" + MAX_TOOL_ROUNDS + "），请简化查询后重试。");
        }

        callback.onThinking(round == 0 ? "正在分析您的问题…" : "正在查询更多数据…");

        AgentMessage assistant = llmClient.chat(messages, toolDefs);

        // 无 tool_calls → 直接返回文本
        if (assistant.getToolCalls() == null || assistant.getToolCalls().isEmpty()) {
            String content = assistant.getContent();
            if (content == null || content.isBlank()) {
                return AgentResponse.error("LLM 返回空内容");
            }
            messages.add(Assistant(assistant)); // keep context record
            return AgentResponse.text(content);
        }

        // 有 tool_calls → 执行并进入下一轮
        if (assistant.getToolCalls().size() > MAX_TOOLS_PER_ROUND) {
            return AgentResponse.error("单轮工具调用过多（" + assistant.getToolCalls().size() + "），最多支持 " + MAX_TOOLS_PER_ROUND + " 个");
        }

        // Add assistant message with tool_calls
        messages.add(AgentMessage.assistantWithToolCalls(assistant.getToolCalls()));

        // Collect charts from tool executions
        Object chartOption = null;

        for (Map<String, Object> toolCall : assistant.getToolCalls()) {
            String toolCallId = (String) toolCall.get("id");
            @SuppressWarnings("unchecked")
            Map<String, Object> func = (Map<String, Object>) toolCall.get("function");
            if (func == null) continue;
            String toolName = (String) func.get("name");
            String arguments = (String) func.get("arguments");
            if (toolName == null) continue;

            callback.onThinking("正在调用工具: " + toolName + "…");

            ToolResult toolResult = toolRegistry.execute(toolName, arguments);

            // Truncate tool result for LLM
            String resultJson = JSONUtil.toJsonStr(toolResult);
            if (resultJson.length() > MAX_TOOL_RESULT_JSON_LENGTH) {
                resultJson = resultJson.substring(0, MAX_TOOL_RESULT_JSON_LENGTH) + "...(已截断)";
            }

            messages.add(AgentMessage.tool(toolCallId, toolName, resultJson));

            // Keep chart from the first tool that returns one
            if (chartOption == null && toolResult.getChart() != null) {
                chartOption = toolResult.getChart();
            }
        }

        // Request LLM again with tool results (keep tools available for multi-round)
        AgentResponse finalResponse = runLoop(messages, toolDefs, round + 1, callback);
        finalResponse.setChart(chartOption);
        return finalResponse;
    }

    /** 辅助：安全获取 assistant content */
    private static AgentMessage Assistant(AgentMessage a) {
        AgentMessage m = new AgentMessage();
        m.setRole("assistant");
        m.setContent(a.getContent());
        m.setToolCalls(a.getToolCalls());
        return m;
    }

    /* ─── 内部类型 ─── */

    @FunctionalInterface
    public interface AgentCallback {
        void onThinking(String message);
    }

    @lombok.Data
    public static class AgentResponse {
        private boolean success;
        private String content;
        private Object chart;
        private List<AgentMessage> toolMessages; // for Conversation saving

        private AgentResponse(boolean success, String content) {
            this.success = success;
            this.content = content;
        }

        static AgentResponse text(String content) {
            return new AgentResponse(true, content);
        }

        static AgentResponse error(String message) {
            return new AgentResponse(false, message);
        }
    }
}
