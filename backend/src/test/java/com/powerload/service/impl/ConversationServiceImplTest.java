package com.powerload.service.impl;

import com.powerload.entity.Conversation;
import com.powerload.mapper.ConversationMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConversationServiceImplTest {

    private final ConversationMapper mapper = mock(ConversationMapper.class);
    private final ConversationServiceImpl service = new ConversationServiceImpl(mapper);

    @Test
    void listsConversationSummariesByRecentActivity() {
        when(mapper.selectList(any())).thenReturn(List.of(
                record(4L, "a", "assistant", "最新回答", 4),
                record(3L, "b", "user", "另一段对话", 3),
                record(2L, "a", "assistant", "第一次回答", 2),
                record(1L, "a", "user", "当前负荷是多少？", 1)
        ));

        var result = service.listConversations(50);

        assertEquals(List.of("a", "b"),
                result.stream().map(item -> item.getConversationId()).toList());
        assertEquals("当前负荷是多少？", result.get(0).getTitle());
        assertEquals("最新回答", result.get(0).getLastMessage());
        assertEquals(3, result.get(0).getMessageCount());
    }

    @Test
    void loadsRecentAgentHistoryInChronologicalOrder() {
        List<Conversation> mapperResult = new ArrayList<>(List.of(
                record(2L, "a", "assistant", "回答", 2),
                record(1L, "a", "user", "问题", 1)
        ));
        when(mapper.selectList(any())).thenReturn(mapperResult);

        var result = service.loadHistory("a", 10);

        assertEquals(List.of("user", "assistant"),
                result.stream().map(item -> item.getRole()).toList());
    }

    @Test
    void loadsConversationMessagesForFrontend() {
        when(mapper.selectList(any())).thenReturn(List.of(
                record(1L, "a", "user", "问题", 1),
                record(2L, "a", "assistant", "回答", 2)
        ));

        var result = service.loadConversation("a");

        assertEquals(2, result.size());
        assertEquals("问题", result.get(0).getContent());
        assertEquals("回答", result.get(1).getContent());
    }

    @Test
    void deletesAllMessagesInConversation() {
        service.deleteConversation("conversation-a");

        verify(mapper).delete(any());
    }

    private Conversation record(long id, String conversationId, String role,
                                String content, int minute) {
        Conversation item = new Conversation();
        item.setId(id);
        item.setConversationId(conversationId);
        item.setRole(role);
        item.setContent(content);
        item.setCreatedAt(LocalDateTime.of(2026, 7, 16, 10, minute));
        return item;
    }
}
