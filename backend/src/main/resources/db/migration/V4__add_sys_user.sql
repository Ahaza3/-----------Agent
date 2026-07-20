-- V4__add_sys_user.sql — 系统用户表 (RBAC)
-- 角色: DISPATCHER / OPERATOR / SYSTEM_ADMIN

CREATE TABLE sys_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
    username        VARCHAR(50)  NOT NULL COMMENT '用户名',
    password_hash   VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码哈希',
    display_name    VARCHAR(100) COMMENT '显示名称',
    role            VARCHAR(30)  NOT NULL DEFAULT 'DISPATCHER' COMMENT '角色',
    email           VARCHAR(100) COMMENT '邮箱',
    is_active       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用 (0=禁用, 1=启用)',
    last_login      DATETIME     COMMENT '最后登录时间',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE INDEX idx_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';
