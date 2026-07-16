package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.agent.AgentMessage;
import com.powerload.entity.Conversation;
import com.powerload.dto.response.ConversationMessageResponse;
import com.powerload.dto.response.ConversationSummary;
import com.powerload.mapper.ConversationMapper;
import com.powerload.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 对话管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;

    @Override
    public void saveMessage(String conversationId, String role, String content, String toolName) {
        Conversation c = new Conversation();
        c.setConversationId(conversationId);
        c.setRole(role);
        c.setContent(content);
        c.setToolName(toolName);
        c.setCreatedAt(LocalDateTime.now());
        conversationMapper.insert(c);
    }

    @Override
    public void saveMessages(String conversationId, List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        var batch = messages.stream().map(m -> {
            Conversation c = new Conversation();
            c.setConversationId(conversationId);
            c.setRole(m.getRole());
            c.setContent(m.getContent());
            c.setToolName(m.getToolName());
            c.setCreatedAt(now);
            return c;
        }).toList();
        for (Conversation c : batch) {
            conversationMapper.insert(c);
        }
    }

    @Override
    public List<AgentMessage> loadHistory(String conversationId, int maxMessages) {
        var wrapper = new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getConversationId, conversationId)
                .orderByDesc(Conversation::getCreatedAt)
                .last("LIMIT " + maxMessages);
        List<Conversation> records = conversationMapper.selectList(wrapper);
        Collections.reverse(records);
        return records.stream().map(r -> {
            AgentMessage m = new AgentMessage();
            m.setRole(r.getRole());
            m.setContent(r.getContent());
            m.setToolName(r.getToolName());
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public List<ConversationSummary> listConversations(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        var wrapper = new LambdaQueryWrapper<Conversation>()
                .orderByDesc(Conversation::getCreatedAt);
        List<Conversation> records = conversationMapper.selectList(wrapper);
        Map<String, ConversationSummary> summaries = new LinkedHashMap<>();

        for (Conversation record : records) {
            ConversationSummary summary = summaries.get(record.getConversationId());
            if (summary == null) {
                if (summaries.size() >= safeLimit) continue;
                summary = new ConversationSummary();
                summary.setConversationId(record.getConversationId());
                summary.setTitle("新对话");
                summary.setUpdatedAt(record.getCreatedAt());
                summaries.put(record.getConversationId(), summary);
            }

            if ("user".equals(record.getRole()) || "assistant".equals(record.getRole())) {
                summary.setMessageCount(summary.getMessageCount() + 1);
                if (summary.getLastMessage() == null) {
                    summary.setLastMessage(abbreviate(record.getContent(), 72));
                }
                // records 按时间倒序遍历，最后覆盖得到最早一条用户消息作为标题。
                if ("user".equals(record.getRole())) {
                    summary.setTitle(abbreviate(record.getContent(), 36));
                }
            }
        }

        return new ArrayList<>(summaries.values());
    }

    @Override
    public List<ConversationMessageResponse> loadConversation(String conversationId) {
        var wrapper = new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getConversationId, conversationId)
                .in(Conversation::getRole, "user", "assistant")
                .orderByAsc(Conversation::getCreatedAt)
                .last("LIMIT 200");
        return conversationMapper.selectList(wrapper).stream().map(record -> {
            ConversationMessageResponse response = new ConversationMessageResponse();
            response.setId(record.getId());
            response.setRole(record.getRole());
            response.setContent(record.getContent());
            response.setCreatedAt(record.getCreatedAt());
            return response;
        }).toList();
    }

    @Override
    public void deleteConversation(String conversationId) {
        var wrapper = new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getConversationId, conversationId);
        conversationMapper.delete(wrapper);
        log.info("Agent 会话已删除: {}", conversationId);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.isBlank()) return "新对话";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength) + "…";
    }
}
