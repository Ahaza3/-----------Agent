package com.powerload.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    @Test
    void shouldRegisterTools() {
        Tool t1 = dummyTool("tool_a");
        Tool t2 = dummyTool("tool_b");
        ToolRegistry registry = new ToolRegistry(List.of(t1, t2));
        assertEquals(2, registry.size());
        assertTrue(registry.contains("tool_a"));
        assertTrue(registry.contains("tool_b"));
    }

    @Test
    void shouldReturnToolDefinitions() {
        ToolRegistry registry = new ToolRegistry(List.of(dummyTool("query_load")));
        var defs = registry.getToolDefinitions();
        assertEquals(1, defs.size());
        assertEquals("function", defs.get(0).get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> func = (Map<String, Object>) defs.get(0).get("function");
        assertEquals("query_load", func.get("name"));
    }

    @Test
    void shouldRejectDuplicateToolName() {
        Tool t1 = dummyTool("same_name");
        Tool t2 = dummyTool("same_name");
        assertThrows(IllegalStateException.class, () -> new ToolRegistry(List.of(t1, t2)));
    }

    @Test
    void shouldReturnErrorForUnknownTool() {
        ToolRegistry registry = new ToolRegistry(List.of(dummyTool("a")));
        ToolResult result = registry.execute("unknown", "{}");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("未知工具"));
    }

    @Test
    void shouldReturnErrorForNullToolName() {
        ToolRegistry registry = new ToolRegistry(List.of(dummyTool("a")));
        ToolResult result = registry.execute(null, "{}");
        assertFalse(result.isSuccess());
    }

    @Test
    void shouldNotThrowOnExecuteException() {
        Tool t = new Tool() {
            @Override public String name() { return "crash"; }
            @Override public String description() { return "crash"; }
            @Override public Map<String, Object> parameters() { return Map.of(); }
            @Override public ToolResult execute(String args) { throw new RuntimeException("bang!"); }
        };
        ToolRegistry registry = new ToolRegistry(List.of(t));
        ToolResult result = registry.execute("crash", "{}");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("bang!"));
    }

    private static Tool dummyTool(String name) {
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return "description of " + name; }
            @Override public Map<String, Object> parameters() { return Map.of("type", "object"); }
            @Override public ToolResult execute(String args) { return ToolResult.ok("ok", null); }
        };
    }
}
