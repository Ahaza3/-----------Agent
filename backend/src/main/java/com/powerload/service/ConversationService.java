package com.powerload.service;

import com.powerload.agent.AgentMessage;

import java.util.List;

/**
 * 对话管理服务 — 保存和加载 Agent 对话历史。
 */
public interface ConversationService {

    /**
     * 保存单条消息
     */
    void saveMessage(String conversationId, String role, String content, String toolName);

    /**
     * 批量保存（按 AgentMessage 列表，role 取自 AgentMessage.role）
     */
    void saveMessages(String conversationId, List<AgentMessage> messages);

    /**
     * 加载对话历史（最多最近 N 条，按 createdAt 升序）
     */
    List<AgentMessage> loadHistory(String conversationId, int maxMessages);
}
