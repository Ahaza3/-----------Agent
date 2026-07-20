-- V7__conversation_owner.sql — Agent 会话按登录用户隔离

ALTER TABLE conversation
    ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER conversation_id,
    ADD COLUMN username VARCHAR(50) NULL COMMENT '所属用户名' AFTER user_id,
    ADD COLUMN user_role VARCHAR(30) NULL COMMENT '所属用户角色' AFTER username,
    ADD INDEX idx_conversation_user (user_id, conversation_id),
    ADD INDEX idx_conversation_user_created (user_id, created_at);

-- 旧版本会话没有登录用户归属，统一归档给默认调度员账号，避免升级后演示历史全部丢失。
UPDATE conversation c
JOIN sys_user u ON u.username = 'dispatcher'
SET c.user_id = u.id,
    c.username = u.username,
    c.user_role = u.role
WHERE c.user_id IS NULL;
