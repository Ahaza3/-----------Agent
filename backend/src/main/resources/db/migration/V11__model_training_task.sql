CREATE TABLE model_training_task (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_name         VARCHAR(30)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    data_start         DATETIME NULL,
    data_end           DATETIME NULL,
    sample_count       INT NULL,
    started_at         DATETIME NOT NULL,
    finished_at        DATETIME NULL,
    duration_ms        BIGINT NULL,
    message            VARCHAR(500) NULL,
    output_tail        TEXT NULL,
    artifact_manifest  TEXT NULL,
    created_at         DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_training_task_created (created_at),
    INDEX idx_training_task_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型训练任务记录';
