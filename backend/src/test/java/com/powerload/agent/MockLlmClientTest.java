package com.powerload.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MockLlmClientTest {

    @Test
    void shouldReturnDefaultResponse() throws LlmException {
        MockLlmClient client = new MockLlmClient();
        AgentMessage resp = client.chat(List.of(), null);
        assertNotNull(resp.getContent());
        assertTrue(resp.getContent().contains("Mock"));
    }

    @Test
    void shouldReturnPresetResponse() throws LlmException {
        MockLlmClient client = new MockLlmClient();
        client.setNextResponse(AgentMessage.assistant("定制回复"));
        assertEquals("定制回复", client.chat(List.of(), null).getContent());
    }

    @Test
    void shouldIncrementCallCount() throws LlmException {
        MockLlmClient client = new MockLlmClient();
        assertEquals(0, client.getCallCount());
        client.chat(List.of(), null);
        assertEquals(1, client.getCallCount());
        client.chat(List.of(), null);
        assertEquals(2, client.getCallCount());
    }

    @Test
    void shouldReturnToolCalls() throws LlmException {
        MockLlmClient client = new MockLlmClient();
        var toolCalls = List.<java.util.Map<String, Object>>of(
                java.util.Map.of("id", "c1", "type", "function",
                        "function", java.util.Map.of("name", "test", "arguments", "{}"))
        );
        client.setNextResponse(AgentMessage.assistantWithToolCalls(toolCalls));
        AgentMessage resp = client.chat(List.of(), List.of());
        assertNotNull(resp.getToolCalls());
        assertEquals(1, resp.getToolCalls().size());
    }
}
