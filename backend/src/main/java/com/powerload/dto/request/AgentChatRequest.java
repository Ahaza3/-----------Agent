package com.powerload.dto.request;

import lombok.Data;

/**
 * Agent 对话请求
 */
@Data
public class AgentChatRequest {

    /** 对话 ID（可选，为空时创建新对话） */
    private String conversationId;

    /** 用户输入消息 */
    private String message;
}
