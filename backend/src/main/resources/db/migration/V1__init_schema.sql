-- ================================================================
-- V1__init_schema.sql — 初始建表（P0 全部表 + conversation）
-- 对应文档: docs/05-数据模型设计.md
-- 日期: 2026-07-11
-- ================================================================

-- ─── 1. load_data — 历史负荷数据表 ───
CREATE TABLE load_data (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
    time        DATETIME NOT NULL COMMENT '数据时间点（精确到小时）',
    load_mw     FLOAT NOT NULL COMMENT '负荷值(MW)',
    temperature FLOAT COMMENT '温度(°C)',
    humidity    FLOAT COMMENT '湿度(%)',
    is_holiday  TINYINT DEFAULT 0 COMMENT '是否节假日(0=否,1=是)',
    hour        TINYINT COMMENT '小时(0-23)',
    day_of_week TINYINT COMMENT '星期几(0=周一,6=周日)',
    month       TINYINT COMMENT '月份(1-12)',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE INDEX idx_time (time),
    INDEX idx_hour (hour),
    INDEX idx_day_of_week (day_of_week),
    INDEX idx_month (month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史负荷数据表';

-- ─── 2. prediction_result — 预测结果表 ───
CREATE TABLE prediction_result (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
    predict_time     DATETIME NOT NULL COMMENT '预测的时间点',
    predicted_load   FLOAT NOT NULL COMMENT '预测负荷值(MW)',
    lower_bound      FLOAT COMMENT '置信区间下界',
    upper_bound      FLOAT COMMENT '置信区间上界',
    model_version_id BIGINT COMMENT '关联模型版本ID',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '预测生成时间',

    INDEX idx_predict_time (predict_time),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预测结果表';

-- ─── 3. alert_event — 告警事件表 ───
CREATE TABLE alert_event (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
    trigger_time    DATETIME NOT NULL COMMENT '触发时间',
    level           VARCHAR(10) NOT NULL COMMENT '告警级别: RED/ORANGE/YELLOW',
    type            VARCHAR(20) NOT NULL DEFAULT 'THRESHOLD' COMMENT '告警类型: THRESHOLD/TREND/ANOMALY',
    current_value   FLOAT COMMENT '当前负荷值(MW)',
    threshold_value FLOAT COMMENT '触发阈值(MW)',
    rule_id         BIGINT COMMENT '关联告警规则ID',
    ai_analysis     TEXT COMMENT 'AI 分析文案（模板生成）',
    suggestion      TEXT COMMENT '建议措施',
    is_read         TINYINT DEFAULT 0 COMMENT '是否已读(0=未读,1=已读)',
    resolved_at     DATETIME COMMENT '告警解决时间',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_trigger_time (trigger_time),
    INDEX idx_level (level),
    INDEX idx_is_read (is_read),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警事件表';

-- ─── 4. alert_rule — 告警规则表 ───
CREATE TABLE alert_rule (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
    name       VARCHAR(100) NOT NULL COMMENT '规则名称',
    type       VARCHAR(50) NOT NULL DEFAULT 'THRESHOLD' COMMENT '规则类型',
    config     JSON NOT NULL COMMENT '规则配置(JSON格式)',
    is_active  TINYINT DEFAULT 1 COMMENT '是否启用(0=禁用,1=启用)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则表';

-- ─── 5. model_version — 模型版本表 ───
CREATE TABLE model_version (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
    model_name  VARCHAR(100) NOT NULL COMMENT '模型名称(LSTM/Prophet)',
    version     VARCHAR(20) NOT NULL COMMENT '版本号(v1.0/v2.0)',
    mape        FLOAT COMMENT 'MAPE精度(%)',
    rmse        FLOAT COMMENT 'RMSE',
    file_path   VARCHAR(500) NOT NULL COMMENT '模型文件路径',
    hyperparams TEXT COMMENT '超参数(JSON)',
    is_active   TINYINT DEFAULT 0 COMMENT '是否活跃(同时只有一个活跃)',
    trained_at  DATETIME COMMENT '训练完成时间',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型版本表';

-- ─── 6. conversation — 对话记录表（新增，对应数据流图 D4 对话记录库）───
CREATE TABLE conversation (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
    conversation_id VARCHAR(64) NOT NULL COMMENT '会话ID(UUID)',
    role            VARCHAR(20) NOT NULL COMMENT '角色: user/assistant/tool',
    content         TEXT NOT NULL COMMENT '消息内容',
    tool_name       VARCHAR(100) COMMENT '工具名称(仅role=tool时)',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_conversation_id (conversation_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话记录表';
