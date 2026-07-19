package com.powerload.service;

import com.powerload.agent.AgentMessage;
import com.powerload.dto.response.ConversationMessageResponse;
import com.powerload.dto.response.ConversationSummary;
import com.powerload.security.SysUserPrincipal;

import java.util.List;

/**
 * 对话管理服务 — 保存和加载 Agent 对话历史。
 */
public interface ConversationService {

    /**
     * 保存单条消息
     */
    void saveMessage(String conversationId, SysUserPrincipal user, String role, String content, String toolName);

    /**
     * Save one message with an optional chart option serialized by the implementation.
     */
    void saveMessage(String conversationId, SysUserPrincipal user, String role, String content, String toolName, Object chartOption);

    /**
     * 批量保存（按 AgentMessage 列表，role 取自 AgentMessage.role）
     */
    void saveMessages(String conversationId, SysUserPrincipal user, List<AgentMessage> messages);

    /**
     * 加载对话历史（最多最近 N 条，按 createdAt 升序）
     */
    List<AgentMessage> loadHistory(String conversationId, SysUserPrincipal user, int maxMessages);

    /** 查询最近更新的会话摘要。 */
    List<ConversationSummary> listConversations(SysUserPrincipal user, int limit);

    /** 查询单个会话供前端回看。 */
    List<ConversationMessageResponse> loadConversation(String conversationId, SysUserPrincipal user);

    /** 删除会话及其全部消息。 */
    void deleteConversation(String conversationId, SysUserPrincipal user);
}
