CREATE TABLE weather_forecast (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    location_code   VARCHAR(64) NOT NULL,
    forecast_time   DATETIME NOT NULL,
    temperature     FLOAT NULL,
    humidity        FLOAT NULL,
    source          VARCHAR(32) NOT NULL,
    issued_at       DATETIME NOT NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,

    UNIQUE INDEX uk_weather_snapshot (location_code, forecast_time, issued_at),
    INDEX idx_weather_lookup (location_code, forecast_time),
    INDEX idx_weather_issued (issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='未来天气预报快照';
