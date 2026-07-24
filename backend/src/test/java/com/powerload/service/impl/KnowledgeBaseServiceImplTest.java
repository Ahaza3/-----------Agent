package com.powerload.service.impl;

import com.powerload.entity.KnowledgeChunk;
import com.powerload.mapper.KnowledgeChunkMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeBaseServiceImplTest {

    @Test
    void shouldRankRelevantChineseKnowledgeChunks() {
        KnowledgeChunkMapper mapper = mock(KnowledgeChunkMapper.class);
        when(mapper.selectEnabled()).thenReturn(List.of(
                chunk(1L, "电网调度常用术语", "调度,术语", "峰值和谷值用于描述负荷曲线特征。"),
                chunk(2L, "智能告警处置规范", "告警,工单,处置", "查看告警详情后，应由调度或运维人员人工确认。"),
                chunk(3L, "负荷预测基础", "预测,MAPE", "MAPE 用于衡量预测值与实际值的相对误差。")
        ));

        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(mapper);

        List<KnowledgeChunk> result = service.search("告警怎么处置", 2);

        assertEquals(1, result.size());
        assertEquals("智能告警处置规范", result.get(0).getTitle());
    }

    @Test
    void shouldReturnEmptyListForBlankQuery() {
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(mock(KnowledgeChunkMapper.class));

        assertTrue(service.search("  ", 3).isEmpty());
    }

    private KnowledgeChunk chunk(Long id, String title, String keywords, String content) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setTitle(title);
        chunk.setKeywords(keywords);
        chunk.setContent(content);
        chunk.setEnabled(1);
        return chunk;
    }
}
