ALTER TABLE model_version
    ADD COLUMN artifact_dir VARCHAR(160) NULL COMMENT 'immutable artifact directory below the configured model root',
    ADD COLUMN artifact_checksum CHAR(64) NULL COMMENT 'SHA-256 of sorted path and file SHA-256 pairs',
    ADD COLUMN runtime_status VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNVERIFIED' COMMENT 'ACTIVE/CANDIDATE/LEGACY_UNVERIFIED/LOAD_FAILED/ROLLBACK_REQUIRED',
    ADD COLUMN deployed_at DATETIME NULL,
    ADD COLUMN last_load_error VARCHAR(500) NULL,
    ADD COLUMN last_health_checked_at DATETIME NULL;

CREATE INDEX idx_model_version_runtime_status ON model_version (runtime_status);
