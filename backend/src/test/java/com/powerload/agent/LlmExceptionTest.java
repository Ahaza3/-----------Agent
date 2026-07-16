package com.powerload.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        LlmException e = new LlmException("LLM API 返回 HTTP 500");
        assertEquals("LLM API 返回 HTTP 500", e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    void shouldCreateWithMessageAndCause() {
        RuntimeException cause = new RuntimeException("timeout");
        LlmException e = new LlmException("连接超时", cause);
        assertEquals("连接超时", e.getMessage());
        assertSame(cause, e.getCause());
    }

    @Test
    void shouldBeCatchableAsException() {
        Exception e = new LlmException("test");
        assertTrue(e instanceof Exception);
    }
}
