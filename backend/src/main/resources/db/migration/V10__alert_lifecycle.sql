ALTER TABLE alert_event
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN acknowledged_at DATETIME NULL,
    ADD COLUMN acknowledged_by BIGINT NULL,
    ADD COLUMN acknowledged_by_name VARCHAR(100) NULL,
    ADD INDEX idx_alert_status (status);

UPDATE alert_event
SET status = CASE
    WHEN resolved_at IS NOT NULL THEN 'RECOVERED'
    ELSE 'ACTIVE'
END;
