package com.powerload.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/** Agent 历史会话摘要。 */
@Data
public class ConversationSummary {

    private String conversationId;
    private String title;
    private String lastMessage;
    private int messageCount;
    private LocalDateTime updatedAt;
}
