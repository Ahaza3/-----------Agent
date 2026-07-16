package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.agent.AgentMessage;
import com.powerload.entity.Conversation;
import com.powerload.mapper.ConversationMapper;
import com.powerload.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
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
                .orderByAsc(Conversation::getCreatedAt)
                .last("LIMIT " + maxMessages);
        List<Conversation> records = conversationMapper.selectList(wrapper);
        return records.stream().map(r -> {
            AgentMessage m = new AgentMessage();
            m.setRole(r.getRole());
            m.setContent(r.getContent());
            m.setToolName(r.getToolName());
            return m;
        }).collect(Collectors.toList());
    }
}
