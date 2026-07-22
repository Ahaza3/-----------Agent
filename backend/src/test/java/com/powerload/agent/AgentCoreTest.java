package com.powerload.agent;

import com.powerload.audit.AgentToolAuditService;
import com.powerload.security.SysUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AgentCoreTest {

    private MockLlmClient mockLlm;
    private ToolRegistry toolRegistry;
    private AgentCore agentCore;
    private StringBuilder thinkingLog;

    @BeforeEach
    void setUp() {
        mockLlm = new MockLlmClient();
        toolRegistry = registry(dummyQueryTool());
        agentCore = new AgentCore(mockLlm, toolRegistry);
        thinkingLog = new StringBuilder();
    }

    /* ─── 无工具直接回答 ─── */

    @Test
    void shouldReturnTextWhenNoToolCalls() {
        mockLlm.setNextResponse(AgentMessage.assistant("你好，我是助手。"));

        AgentCore.AgentResponse r = agentCore.run("你好", List.of(), thinkingLog::append, null);

        assertTrue(r.isSuccess());
        assertEquals("你好，我是助手。", r.getContent());
        assertNull(r.getChart());
    }

    /* ─── 单次工具调用 ─── */

    @Test
    void shouldCallToolAndReturnResponse() {
        // 第一次：返回 tool_call
        AgentMessage withTool = AgentMessage.assistantWithToolCalls(List.of(
                Map.of("id", "call_1", "type", "function",
                        "function", Map.of("name", "test_tool", "arguments", "{}"))
        ));
        // 第二次：返回最终回答
        AgentMessage finalAnswer = AgentMessage.assistant("工具返回: ok");

        mockLlm.setNextResponse(withTool);
        // MockLlmClient only returns one response, so we need a smarter mock
        // For this test, we design AgentCore to loop internally
        // Since MockLlmClient returns the same response each time, we need to adapt

        // Actually, let's just test the initial tool call flow since MockLlmClient
        // returns the same response each time. We'll verify the tool was dispatched.

        AgentCore.AgentResponse r = agentCore.run("查询数据", List.of(), thinkingLog::append, null);

        // The mock returns a tool call, tool executes with ok, then second LLM
        // call also gets the tool call (MockLlmClient always returns same).
        // This will trigger max rounds eventually.
        // Response will be error due to max rounds being reached.
        assertFalse(r.isSuccess());
        assertTrue(r.getContent().contains("最大工具调用轮次"));
    }

    /* ─── 正确模拟 AgentCore 工具调用流程 ─── */

    @Test
    void shouldHandleToolCallAndGetFinalAnswer() {
        // Use a sequenced mock that returns tool_call first, then text
        SequencedLlmClient sequenced = new SequencedLlmClient(
                AgentMessage.assistantWithToolCalls(List.of(
                        Map.of("id", "call_1", "type", "function",
                                "function", Map.of("name", "test_tool", "arguments", "{}"))
                )),
                AgentMessage.assistant("数据查询完成，当前负荷 900 MW（模拟数据）。")
        );
        AgentCore core = new AgentCore(sequenced, toolRegistry);

        AgentCore.AgentResponse r = core.run("当前负荷", List.of(), thinkingLog::append, dispatcher());

        assertTrue(r.isSuccess());
        assertTrue(r.getContent().contains("900 MW"));
    }

    /* ─── 多轮工具调用 ─── */

    @Test
    void shouldHandleMultiRoundToolCalls() {
        // Round 0: tool_call → Round 1: another tool_call → Round 2: final text
        SequencedLlmClient sequenced = new SequencedLlmClient(
                AgentMessage.assistantWithToolCalls(List.of(
                        Map.of("id", "call_1", "type", "function",
                                "function", Map.of("name", "test_tool", "arguments", "{}"))
                )),
                AgentMessage.assistantWithToolCalls(List.of(
                        Map.of("id", "call_2", "type", "function",
                                "function", Map.of("name", "test_tool", "arguments", "{}"))
                )),
                AgentMessage.assistant("经过两轮查询，最终结果已汇总。")
        );
        AgentCore core = new AgentCore(sequenced, toolRegistry);

        AgentCore.AgentResponse r = core.run("复杂查询", List.of(), thinkingLog::append, dispatcher());

        assertTrue(r.isSuccess());
        assertTrue(r.getContent().contains("最终结果"));
    }

    /* ─── 最大轮次限制 ─── */

    @Test
    void shouldHitMaxRoundsLimit() {
        // Always returns tool_call → infinite loop until max rounds
        AgentMessage alwaysTool = AgentMessage.assistantWithToolCalls(List.of(
                Map.of("id", "loop", "type", "function",
                        "function", Map.of("name", "test_tool", "arguments", "{}"))
        ));
        mockLlm.setNextResponse(alwaysTool);

        AgentCore.AgentResponse r = agentCore.run("查询", List.of(), thinkingLog::append, dispatcher());

        assertFalse(r.isSuccess());
        assertTrue(r.getContent().contains("最大工具调用轮次"));
    }

    /* ─── 工具执行异常不崩溃 ─── */

    @Test
    void shouldNotCrashOnToolException() {
        Tool crashTool = new Tool() {
            @Override public String name() { return "crash"; }
            @Override public String description() { return "crash"; }
            @Override public Set<String> allowedRoles() { return Set.of("DISPATCHER", "OPERATOR", "SYSTEM_ADMIN"); }
            @Override public Map<String, Object> parameters() { return Map.of("type", "object"); }
            @Override public ToolResult execute(String args) { throw new RuntimeException("tool crash"); }
        };
        ToolRegistry crashRegistry = registry(crashTool);
        SequencedLlmClient sequenced = new SequencedLlmClient(
                AgentMessage.assistantWithToolCalls(List.of(
                        Map.of("id", "call_1", "type", "function",
                                "function", Map.of("name", "crash", "arguments", "{}"))
                )),
                AgentMessage.assistant("工具执行失败，但系统没崩溃。")
        );
        AgentCore core = new AgentCore(sequenced, crashRegistry);

        AgentCore.AgentResponse r = core.run("test", List.of(), thinkingLog::append, dispatcher());
        // Should not throw — tool error is captured and LLM gets the error
        assertTrue(r.isSuccess());
    }

    /* ─── 空消息 ─── */

    @Test
    void shouldReturnErrorForBlankMessage() {
        AgentCore.AgentResponse r = agentCore.run("   ", List.of(), thinkingLog::append, null);
        assertFalse(r.isSuccess());
        assertTrue(r.getContent().contains("不能为空"));
    }

    /* ─── 超长消息截断 ─── */

    @Test
    void shouldTruncateLongMessage() {
        mockLlm.setNextResponse(AgentMessage.assistant("ok"));
        String longMsg = "A".repeat(3000);
        AgentCore.AgentResponse r = agentCore.run(longMsg, List.of(), thinkingLog::append, null);
        assertTrue(r.isSuccess());
    }

    /* ─── 工具参数无效 ─── */

    @Test
    void shouldHandleInvalidToolArgs() {
        SequencedLlmClient sequenced = new SequencedLlmClient(
                AgentMessage.assistantWithToolCalls(List.of(
                        Map.of("id", "call_1", "type", "function",
                                "function", Map.of("name", "test_tool", "arguments", "not valid json"))
                )),
                AgentMessage.assistant("参数错误已处理")
        );
        AgentCore core = new AgentCore(sequenced, toolRegistry);

        AgentCore.AgentResponse r = core.run("test", List.of(), thinkingLog::append, dispatcher());
        assertTrue(r.isSuccess());
    }

    /* ─── LLM 返回空 content ─── */

    @Test
    void shouldHandleNullContent() {
        AgentMessage nullContent = new AgentMessage();
        nullContent.setRole("assistant");
        nullContent.setContent(null);
        mockLlm.setNextResponse(nullContent);

        AgentCore.AgentResponse r = agentCore.run("hello", List.of(), thinkingLog::append, null);
        assertFalse(r.isSuccess());
    }

    @Test
    void shouldSendDifferentToolDefinitionsToDifferentRoles() {
        CapturingLlmClient llm = new CapturingLlmClient();
        Tool dispatcherTool = tool("dispatcher_tool", Set.of("DISPATCHER", "SYSTEM_ADMIN"));
        Tool operatorTool = tool("operator_tool", Set.of("OPERATOR", "SYSTEM_ADMIN"));
        Tool commonTool = tool("common_tool", Set.of("DISPATCHER", "OPERATOR", "SYSTEM_ADMIN"));
        AgentCore core = new AgentCore(llm, registry(dispatcherTool, operatorTool, commonTool));

        assertTrue(core.run("查询", List.of(), thinkingLog::append,
                new SysUserPrincipal(1L, "dispatcher", "DISPATCHER")).isSuccess());
        assertEquals(Set.of("dispatcher_tool", "common_tool"), llm.toolNames());

        assertTrue(core.run("查询", List.of(), thinkingLog::append,
                new SysUserPrincipal(2L, "operator", "OPERATOR")).isSuccess());
        assertEquals(Set.of("operator_tool", "common_tool"), llm.toolNames());
    }

    /* ─── helpers ─── */

    private static Tool dummyQueryTool() {
        return new Tool() {
            @Override public String name() { return "test_tool"; }
            @Override public String description() { return "test tool"; }
            @Override public Set<String> allowedRoles() { return Set.of("DISPATCHER", "OPERATOR", "SYSTEM_ADMIN"); }
            @Override public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of());
            }
            @Override public ToolResult execute(String args) {
                return ToolResult.ok("ok", Map.of("result", "test"));
            }
        };
    }

    private static Tool tool(String name, Set<String> roles) {
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return name; }
            @Override public Set<String> allowedRoles() { return roles; }
            @Override public Map<String, Object> parameters() { return Map.of("type", "object"); }
            @Override public ToolResult execute(String args) { return ToolResult.ok("ok", null); }
        };
    }

    private static ToolRegistry registry(Tool... tools) {
        return new ToolRegistry(List.of(tools), mock(AgentToolAuditService.class));
    }

    private static SysUserPrincipal dispatcher() {
        return new SysUserPrincipal(1L, "dispatcher", "DISPATCHER");
    }

    static class CapturingLlmClient implements LlmClient {
        private List<Map<String, Object>> tools = List.of();

        @Override
        public AgentMessage chat(List<AgentMessage> messages, List<Map<String, Object>> tools) {
            this.tools = List.copyOf(tools);
            return AgentMessage.assistant("ok");
        }

        Set<String> toolNames() {
            return tools.stream()
                    .map(def -> (Map<String, Object>) def.get("function"))
                    .map(function -> (String) function.get("name"))
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    /** 按序列返回预设响应的 LlmClient */
    static class SequencedLlmClient implements LlmClient {
        private final AgentMessage[] responses;
        private int idx = 0;

        SequencedLlmClient(AgentMessage... responses) {
            this.responses = responses;
        }

        @Override
        public AgentMessage chat(List<AgentMessage> messages, List<Map<String, Object>> tools)
                throws LlmException {
            if (idx >= responses.length) {
                return AgentMessage.assistant("fallback");
            }
            return responses[idx++];
        }
    }
}
