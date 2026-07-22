CREATE TABLE forecast_run (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id                  VARCHAR(64) NOT NULL COMMENT '对外展示的预测批次标识',
    status                  VARCHAR(16) NOT NULL COMMENT 'RUNNING/COMPLETED/FAILED',
    issued_at               DATETIME NOT NULL COMMENT '预测签发时间，Asia/Shanghai',
    data_cutoff             DATETIME NULL COMMENT '本次输入负荷数据截止时间',
    forecast_horizon_hours  INT NOT NULL COMMENT '预测范围，小时',
    node_id                 BIGINT NULL COMMENT '预测拓扑节点 ID',
    model_version_id        BIGINT NULL COMMENT '模型版本引用',
    model_version           VARCHAR(128) NULL COMMENT '运行时模型版本快照',
    artifact_checksum       VARCHAR(128) NOT NULL COMMENT '模型产物 SHA-256 或不可用标识',
    weather_issued_at       DATETIME NULL COMMENT '实际读取的天气预报批次签发时间',
    weather_fallback_reason VARCHAR(128) NULL COMMENT '天气未形成单一完整批次的原因',
    prediction_count        INT NOT NULL DEFAULT 0 COMMENT '成功持久化的预测点数量',
    failure_reason          VARCHAR(256) NULL COMMENT '脱敏、长度受限的失败原因',
    idempotency_key         VARCHAR(128) NULL COMMENT '客户端可复用幂等键',
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at            DATETIME NULL,
    UNIQUE INDEX uk_forecast_run_run_id (run_id),
    INDEX idx_forecast_run_node_issued (node_id, issued_at),
    INDEX idx_forecast_run_status (status),
    INDEX idx_forecast_run_issued_at (issued_at),
    INDEX idx_forecast_run_idempotency (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='负荷预测批次追溯记录';

ALTER TABLE prediction_result
    ADD COLUMN forecast_run_id BIGINT NULL COMMENT '关联 forecast_run 主键，历史数据保持 NULL' AFTER id,
    ADD COLUMN lead_hours INT NULL COMMENT '相对预测签发时刻的提前量（小时）' AFTER forecast_run_id,
    ADD UNIQUE INDEX uk_prediction_run_predict_time (forecast_run_id, predict_time),
    ADD INDEX idx_prediction_forecast_run (forecast_run_id);
