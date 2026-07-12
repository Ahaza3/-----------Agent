package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话记录表 — conversation
 */
@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID(UUID) */
    private String conversationId;

    /** 角色: user/assistant/tool */
    private String role;

    /** 消息内容 */
    private String content;

    /** 工具名称(仅role=tool时) */
    private String toolName;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
