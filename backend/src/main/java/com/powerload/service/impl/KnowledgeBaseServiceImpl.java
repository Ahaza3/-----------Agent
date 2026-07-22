package com.powerload.service.impl;

import com.powerload.entity.KnowledgeChunk;
import com.powerload.mapper.KnowledgeChunkMapper;
import com.powerload.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 轻量知识检索实现。
 *
 * <p>当前知识规模较小，使用 MySQL 读取启用切片后在 Java 中做相关度排序，
 * 避免为实训项目引入额外向量数据库。后续可将 score 替换为 embedding 相似度。</p>
 */
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^\\p{L}\\p{N}\\u4e00-\\u9fff]+");
    private static final int MAX_QUERY_LENGTH = 200;

    private final KnowledgeChunkMapper knowledgeChunkMapper;

    public KnowledgeBaseServiceImpl(KnowledgeChunkMapper knowledgeChunkMapper) {
        this.knowledgeChunkMapper = knowledgeChunkMapper;
    }

    @Override
    public List<KnowledgeChunk> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.length() > MAX_QUERY_LENGTH) {
            normalizedQuery = normalizedQuery.substring(0, MAX_QUERY_LENGTH);
        }

        Set<String> terms = extractTerms(normalizedQuery);
        List<ScoredChunk> scored = new ArrayList<>();
        for (KnowledgeChunk chunk : knowledgeChunkMapper.selectEnabled()) {
            int score = score(chunk, normalizedQuery, terms);
            if (score > 0) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }

        int safeLimit = Math.max(1, Math.min(limit, 10));
        return scored.stream()
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed()
                        .thenComparing(item -> item.chunk().getId()))
                .limit(safeLimit)
                .map(ScoredChunk::chunk)
                .toList();
    }

    private int score(KnowledgeChunk chunk, String query, Set<String> terms) {
        String title = lower(chunk.getTitle());
        String keywords = lower(chunk.getKeywords());
        String content = lower(chunk.getContent());
        int score = 0;

        if (title.contains(query)) score += 30;
        if (keywords.contains(query)) score += 24;
        if (content.contains(query)) score += 18;

        for (String term : terms) {
            if (term.length() < 2) continue;
            if (title.contains(term)) score += 8;
            if (keywords.contains(term)) score += 6;
            if (content.contains(term)) score += 3;
        }
        return score;
    }

    private Set<String> extractTerms(String query) {
        Set<String> terms = new HashSet<>();
        for (String token : TOKEN_SPLITTER.split(query)) {
            if (token.length() >= 2) {
                terms.add(token);
            }
            if (containsChinese(token)) {
                for (int i = 0; i < token.length() - 1; i++) {
                    terms.add(token.substring(i, i + 2));
                }
            }
        }
        return terms;
    }

    private boolean containsChinese(String value) {
        return value.codePoints().anyMatch(codePoint -> codePoint >= 0x4e00 && codePoint <= 0x9fff);
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record ScoredChunk(KnowledgeChunk chunk, int score) {
    }
}
