-- V6__alert_ticket.sql — 告警处置工单 + 业务时间线

CREATE TABLE alert_ticket (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_no         VARCHAR(32)   NOT NULL COMMENT '工单编号',
    alert_id          BIGINT        NOT NULL COMMENT '关联告警ID',
    priority          VARCHAR(16)   NOT NULL DEFAULT 'NORMAL' COMMENT '优先级',
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    summary           VARCHAR(500)  COMMENT '工单概要',
    assignee_user_id  BIGINT        COMMENT '指派处理人ID',
    assignee_name     VARCHAR(100)  COMMENT '指派处理人姓名',
    created_by        BIGINT        NOT NULL COMMENT '创建人ID',
    created_by_name   VARCHAR(100)  COMMENT '创建人姓名',
    resolution        TEXT          COMMENT '处理结果说明',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    assigned_at       DATETIME,
    started_at        DATETIME,
    resolved_at       DATETIME,
    closed_at         DATETIME,
    cancelled_at      DATETIME,
    updated_at        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version           INT DEFAULT 0 COMMENT '乐观锁版本号',

    UNIQUE INDEX idx_ticket_no (ticket_no),
    UNIQUE INDEX idx_ticket_alert (alert_id),
    INDEX idx_ticket_status (status),
    INDEX idx_ticket_assignee (assignee_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警处置工单';

CREATE TABLE alert_ticket_action (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id       BIGINT        NOT NULL COMMENT '工单ID',
    action          VARCHAR(30)   NOT NULL COMMENT '操作: CREATE/ASSIGN/CLAIM/START/RESOLVE/CLOSE/CANCEL',
    from_status     VARCHAR(20)   COMMENT '变更前状态',
    to_status       VARCHAR(20)   COMMENT '变更后状态',
    operator_id     BIGINT        COMMENT '操作人ID',
    operator_name   VARCHAR(100)  COMMENT '操作人姓名',
    operator_role   VARCHAR(30)   COMMENT '操作人角色',
    note            TEXT          COMMENT '操作备注',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_ticket_action (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单操作时间线';
