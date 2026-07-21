-- 电力行业知识库：文档切片与轻量关键词检索
CREATE TABLE knowledge_chunk (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    title        VARCHAR(200) NOT NULL COMMENT '知识文档标题',
    category     VARCHAR(50) NOT NULL DEFAULT 'POWER_OPERATION' COMMENT '知识分类',
    source_name  VARCHAR(200) NOT NULL COMMENT '来源文件或资料名称',
    chunk_index  INT NOT NULL COMMENT '文档切片序号',
    content      TEXT NOT NULL COMMENT '知识片段内容',
    keywords     VARCHAR(500) COMMENT '检索关键词，逗号分隔',
    enabled      TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE INDEX uk_knowledge_source_chunk (source_name, chunk_index),
    INDEX idx_knowledge_category (category),
    INDEX idx_knowledge_enabled (enabled),
    FULLTEXT INDEX ft_knowledge_text (title, content, keywords)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电力行业知识库切片';
