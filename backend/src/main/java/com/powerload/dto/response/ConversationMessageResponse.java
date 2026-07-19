package com.powerload.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/** 前端历史回看使用的会话消息。 */
@Data
public class ConversationMessageResponse {

    private Long id;
    private String role;
    private String content;
    private String chartOption;
    private LocalDateTime createdAt;
}
