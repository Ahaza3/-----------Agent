package com.powerload.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.entity.KnowledgeChunk;
import com.powerload.mapper.KnowledgeChunkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** 将 classpath 中内置的电力行业 Markdown 资料导入知识库。 */
@Slf4j
@Component
public class KnowledgeBaseInitializer implements CommandLineRunner {

    private static final String RESOURCE_PATTERN = "classpath*:knowledge/*.md";
    private static final int CHUNK_SIZE = 700;

    private final KnowledgeChunkMapper knowledgeChunkMapper;

    public KnowledgeBaseInitializer(KnowledgeChunkMapper knowledgeChunkMapper) {
        this.knowledgeChunkMapper = knowledgeChunkMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(RESOURCE_PATTERN);
        Arrays.sort(resources, Comparator.comparing(this::resourceName));
        for (Resource resource : resources) {
            importResource(resource);
        }
        log.info("电力行业知识库初始化完成，共导入 {} 个文档", resources.length);
    }

    private void importResource(Resource resource) throws IOException {
        String sourceName = resourceName(resource);
        String document;
        try (InputStream inputStream = resource.getInputStream()) {
            document = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
        List<KnowledgeChunk> chunks = split(sourceName, document);

        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getSourceName, sourceName));
        for (KnowledgeChunk chunk : chunks) {
            knowledgeChunkMapper.insert(chunk);
        }
        log.info("知识文档已导入: source={}, chunks={}", sourceName, chunks.size());
    }

    private List<KnowledgeChunk> split(String sourceName, String document) {
        String title = sourceName.replaceFirst("\\.md$", "");
        String category = "POWER_OPERATION";
        List<KnowledgeChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int index = 0;

        for (String paragraph : document.split("\\R\\s*\\R")) {
            String text = paragraph.trim();
            if (text.isBlank()) continue;
            if (text.startsWith("# ")) {
                title = text.substring(2).trim();
            }
            if (text.startsWith("> 分类：")) {
                category = text.substring("> 分类：".length()).trim();
            }
            if (current.length() > 0 && current.length() + text.length() + 2 > CHUNK_SIZE) {
                chunks.add(createChunk(title, category, sourceName, index++, current.toString()));
                current.setLength(0);
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(text);
        }
        if (current.length() > 0) {
            chunks.add(createChunk(title, category, sourceName, index, current.toString()));
        }
        return chunks;
    }

    private KnowledgeChunk createChunk(String title, String category, String sourceName,
                                       int index, String content) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setTitle(title);
        chunk.setCategory(category);
        chunk.setSourceName(sourceName);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setKeywords(title + "," + category);
        chunk.setEnabled(1);
        chunk.setCreatedAt(LocalDateTime.now());
        chunk.setUpdatedAt(LocalDateTime.now());
        return chunk;
    }

    private String resourceName(Resource resource) {
        return resource.getFilename() == null ? resource.getDescription() : resource.getFilename();
    }
}
