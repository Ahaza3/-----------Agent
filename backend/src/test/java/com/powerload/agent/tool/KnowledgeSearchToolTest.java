package com.powerload.agent.tool;

import com.powerload.agent.ToolResult;
import com.powerload.entity.KnowledgeChunk;
import com.powerload.service.KnowledgeBaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeSearchToolTest {

    private KnowledgeSearchTool tool;
    private KnowledgeBaseService knowledgeBaseService;

    @BeforeEach
    void setUp() {
        knowledgeBaseService = mock(KnowledgeBaseService.class);
        tool = new KnowledgeSearchTool(knowledgeBaseService);
    }

    @Test
    void shouldExposeKnowledgeSearchDefinition() {
        assertEquals("knowledge_search", tool.name());
        assertTrue(tool.description().contains("电力行业"));
        assertTrue(tool.parameters().toString().contains("query"));
    }

    @Test
    void shouldRejectMissingQuery() {
        ToolResult result = tool.execute("{}");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("query"));
    }

    @Test
    void shouldReturnKnowledgeResultsAndProvenance() {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setTitle("电力负荷预测基础");
        chunk.setCategory("LOAD_FORECAST");
        chunk.setSourceName("负荷预测基础.md");
        chunk.setChunkIndex(0);
        chunk.setContent("MAPE 用于衡量预测值与实际值的相对误差。");
        when(knowledgeBaseService.search("MAPE", 4)).thenReturn(List.of(chunk));

        ToolResult result = tool.execute("{\"query\":\"MAPE\"}");

        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("1"));
        assertNotNull(result.getProvenance());
        assertEquals("POWER_KNOWLEDGE_BASE", result.getProvenance().get("source"));
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) result.getData();
        assertEquals(true, data.get("matched"));
        assertEquals(1, ((List<?>) data.get("results")).size());
    }

    @Test
    void shouldClampRequestedLimit() {
        when(knowledgeBaseService.search("告警", 6)).thenReturn(List.of());

        ToolResult result = tool.execute("{\"query\":\"告警\",\"limit\":99}");

        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("未检索到"));
    }
}
