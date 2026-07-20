package com.powerload.config;

import com.powerload.agent.LlmClient;
import com.powerload.agent.MockLlmClient;
import com.powerload.agent.OpenAiCompatibleLlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 客户端配置 — 根据 API Key 是否存在选择实现。
 *
 * <p>API Key 为空时使用 MockLlmClient，应用仍可启动，用户调用 Agent 时
 * 会收到明确的"LLM API Key 未配置"错误。</p>
 */
@Slf4j
@Configuration
public class LlmClientConfig {

    @Value("${llm.api.key:}")
    private String apiKey;

    @Value("${llm.api.url:https://api.deepseek.com/v1}")
    private String apiUrl;

    @Value("${llm.api.model:deepseek-chat}")
    private String model;

    @Value("${llm.api.timeout-seconds:30}")
    private int timeoutSeconds;

    @Bean
    public LlmClient llmClient() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("LLM_API_KEY 未配置，使用 MockLlmClient。Agent 将以模拟模式运行。");
            return new MockLlmClient();
        }
        log.info("LLM 客户端: url={}, model={}, timeout={}s", apiUrl, model, timeoutSeconds);
        return new OpenAiCompatibleLlmClient(apiKey, apiUrl, model, timeoutSeconds);
    }
}
