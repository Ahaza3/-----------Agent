-- Support tickets created from forecast-based prewarning risks.

ALTER TABLE alert_ticket
    MODIFY alert_id BIGINT NULL COMMENT 'Related alert id; null for prewarning tickets',
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'ALERT' COMMENT 'Ticket source: ALERT/PREWARNING' AFTER alert_id,
    ADD COLUMN risk_level VARCHAR(10) NULL COMMENT 'Forecast risk level: RED/ORANGE/YELLOW' AFTER source_type,
    ADD COLUMN forecast_time DATETIME NULL COMMENT 'Forecast risk time' AFTER risk_level,
    ADD COLUMN expected_load FLOAT NULL COMMENT 'Forecast load in MW' AFTER forecast_time,
    ADD INDEX idx_ticket_source (source_type),
    ADD INDEX idx_ticket_forecast_time (forecast_time);
