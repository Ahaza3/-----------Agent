-- V5__operation_log_and_alert_advice.sql

-- 操作日志表
CREATE TABLE operation_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT        COMMENT '操作用户ID',
    username        VARCHAR(50)   COMMENT '用户名',
    role            VARCHAR(30)   COMMENT '角色',
    module          VARCHAR(50)   COMMENT '模块',
    action          VARCHAR(50)   COMMENT '操作',
    request_method  VARCHAR(10)   COMMENT 'HTTP方法',
    request_path    VARCHAR(200)  COMMENT '请求路径',
    result          VARCHAR(20)   COMMENT '结果 (SUCCESS/FAILURE)',
    ip_address      VARCHAR(50)   COMMENT 'IP地址',
    duration_ms     BIGINT        COMMENT '耗时(ms)',
    detail          VARCHAR(500)  COMMENT '脱敏详情',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_op_log_created (created_at),
    INDEX idx_op_log_user (user_id),
    INDEX idx_op_log_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';

-- AI告警建议表
CREATE TABLE alert_advice (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    alert_id        BIGINT        NOT NULL COMMENT '关联告警事件ID',
    audience_role   VARCHAR(30)   NOT NULL COMMENT '目标角色',
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SUCCESS/FAILED/FALLBACK',
    analysis        TEXT          COMMENT 'AI分析',
    actions         TEXT          COMMENT '措施建议(JSON)',
    evidence        TEXT          COMMENT '证据摘要(JSON)',
    model_name      VARCHAR(50)   COMMENT '模型名称',
    error_message   VARCHAR(500)  COMMENT '错误信息',
    generated_at    DATETIME      COMMENT '生成时间',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE INDEX idx_advice_alert_role (alert_id, audience_role),
    INDEX idx_advice_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI告警角色化建议';
