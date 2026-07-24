package com.powerload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powerload.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 知识库切片 Mapper。 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    @Select("SELECT id, title, category, source_name, chunk_index, content, keywords, enabled, "
            + "created_at, updated_at FROM knowledge_chunk WHERE enabled = 1 ORDER BY id")
    List<KnowledgeChunk> selectEnabled();
}
