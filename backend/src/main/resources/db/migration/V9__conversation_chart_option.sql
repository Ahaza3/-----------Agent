-- Persist assistant chart options so Agent conversation history can restore ECharts views after refresh.
ALTER TABLE conversation
    ADD COLUMN chart_option LONGTEXT NULL COMMENT 'Assistant chart option JSON' AFTER tool_name;
