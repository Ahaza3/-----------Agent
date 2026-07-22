package com.powerload.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import com.powerload.entity.KnowledgeChunk;
import com.powerload.service.KnowledgeBaseService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 检索电力行业知识库，为 Agent 提供有来源的业务知识片段。 */
@Component
public class KnowledgeSearchTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_LIMIT = 4;
    private static final int MAX_LIMIT = 6;
    private static final int MAX_CONTENT_LENGTH = 1200;

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeSearchTool(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public String name() {
        return "knowledge_search";
    }

    @Override
    public String description() {
        return "检索电力行业知识库，适用于负荷预测原理、异常识别、调度术语、智能告警处置等问题。"
                + "返回知识片段和来源。涉及行业知识时必须优先调用，不能用常识替代知识库依据。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "要检索的电力行业问题或关键词");
        properties.put("query", query);

        Map<String, Object> limit = new LinkedHashMap<>();
        limit.put("type", "integer");
        limit.put("description", "返回片段数量，默认 4，最大 6");
        properties.put("limit", limit);

        schema.put("properties", properties);
        schema.put("required", List.of("query"));
        return schema;
    }

    @Override
    public ToolResult execute(String argumentsJson) {
        JsonNode args;
        try {
            args = MAPPER.readTree(argumentsJson);
        } catch (JsonProcessingException e) {
            return ToolResult.fail("参数 JSON 解析失败: " + e.getMessage());
        }

        String query = args.has("query") ? args.get("query").asText() : "";
        if (query.isBlank()) {
            return ToolResult.fail("缺少必填参数: query");
        }

        int limit = args.has("limit") ? args.get("limit").asInt(DEFAULT_LIMIT) : DEFAULT_LIMIT;
        limit = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<KnowledgeChunk> chunks = knowledgeBaseService.search(query, limit);
        List<Map<String, Object>> results = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("title", chunk.getTitle());
            result.put("category", chunk.getCategory());
            result.put("source", chunk.getSourceName());
            result.put("chunkIndex", chunk.getChunkIndex());
            result.put("content", truncate(chunk.getContent()));
            results.add(result);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("matched", !results.isEmpty());
        data.put("results", results);
        data.put("notice", results.isEmpty() ? "知识库未检索到相关依据" : "回答时请引用匹配片段，不要超出片段内容编造事实");

        ToolResult toolResult = ToolResult.ok(
                results.isEmpty() ? "知识库未检索到相关内容" : "检索到 " + results.size() + " 条相关知识片段",
                data);
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("source", "POWER_KNOWLEDGE_BASE");
        provenance.put("query", query);
        provenance.put("matchedCount", results.size());
        toolResult.setProvenance(provenance);
        return toolResult;
    }

    private String truncate(String content) {
        if (content == null || content.length() <= MAX_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_CONTENT_LENGTH) + "...(片段已截断)";
    }
}
