package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话记录实体 — 对应 conversation 表
 *
 * <p>记录 Agent 与用户的完整对话历史，用于多轮对话上下文。</p>
 */
@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID (UUID) */
    private String conversationId;

    /** 所属用户 ID */
    private Long userId;

    /** 所属用户名 */
    private String username;

    /** 所属用户角色 */
    private String userRole;

    /** 角色: user / assistant / tool */
    private String role;

    /** 消息内容 */
    private String content;

    /** 工具名称 (仅 role=tool 时) */
    private String toolName;

    /** Assistant chart option JSON, used to restore charts in conversation history. */
    private String chartOption;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
