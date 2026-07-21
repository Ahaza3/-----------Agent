package com.powerload.service;

import com.powerload.entity.KnowledgeChunk;

import java.util.List;

/** 电力行业知识库检索服务。 */
public interface KnowledgeBaseService {

    /**
     * 按用户问题检索最相关的知识片段。
     *
     * @param query 用户问题
     * @param limit 最大返回数量
     * @return 按相关度倒序排列的知识片段
     */
    List<KnowledgeChunk> search(String query, int limit);
}
