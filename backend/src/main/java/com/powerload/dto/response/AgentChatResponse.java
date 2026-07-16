package com.powerload.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 对话响应 — SSE done 事件使用的封装
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponse {

    private String conversationId;
    private String message;
}
