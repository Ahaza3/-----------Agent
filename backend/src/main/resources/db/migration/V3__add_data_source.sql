-- V3__add_data_source.sql — 为 load_data 增加数据来源字段
-- 用于区分 MOCK_HISTORY（原始历史）和 RECOVERED_SIMULATION（补齐恢复数据）

ALTER TABLE load_data
    ADD COLUMN data_source VARCHAR(32) DEFAULT 'MOCK_HISTORY' COMMENT '数据来源: MOCK_HISTORY / RECOVERED_SIMULATION';

UPDATE load_data SET data_source = 'MOCK_HISTORY' WHERE data_source IS NULL;
