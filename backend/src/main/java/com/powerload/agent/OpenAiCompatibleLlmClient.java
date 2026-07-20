package com.powerload.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible LLM 客户端 — 使用 OkHttp 调用 DeepSeek / Qwen 等 API。
 *
 * <p>负责 HTTP 和 JSON，不处理业务逻辑。日志不打印 API Key。</p>
 */
@Slf4j
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public OpenAiCompatibleLlmClient(String apiKey, String apiUrl, String model, int timeoutSeconds) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl.endsWith("/chat/completions") ? apiUrl : apiUrl + "/chat/completions";
        this.model = model;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .readTimeout(Duration.ofSeconds(timeoutSeconds))
                .writeTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Override
    public AgentMessage chat(List<AgentMessage> messages, List<Map<String, Object>> tools)
            throws LlmException {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", buildMessagesArray(messages));
        body.put("temperature", 0.3);
        body.put("max_tokens", 2048);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        String json;
        try {
            json = mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new LlmException("构建 LLM 请求 JSON 失败", e);
        }

        log.debug("LLM 请求: model={}, messages={}, hasTools={}", model, messages.size(), tools != null);

        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        String responseBody;
        try (Response response = httpClient.newCall(request).execute()) {
            responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("LLM HTTP {} (body 已省略)", response.code());
                throw new LlmException("LLM API 返回 HTTP " + response.code());
            }
        } catch (IOException e) {
            log.error("LLM 网络异常", e);
            throw new LlmException("LLM API 请求失败: " + e.getMessage(), e);
        }

        return parseResponse(responseBody);
    }

    private AgentMessage parseResponse(String responseBody) throws LlmException {
        JsonNode root;
        try {
            root = mapper.readTree(responseBody);
        } catch (JsonProcessingException e) {
            log.error("LLM 响应 JSON 解析失败: {}", truncateForLog(responseBody));
            throw new LlmException("LLM 响应 JSON 解析失败");
        }

        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new LlmException("LLM 返回空 choices");
        }

        JsonNode message = choices.get(0).get("message");
        if (message == null) {
            throw new LlmException("LLM 返回 message 为空");
        }

        AgentMessage result = new AgentMessage();
        result.setRole("assistant");

        JsonNode contentNode = message.get("content");
        result.setContent(contentNode != null && !contentNode.isNull() ? contentNode.asText() : null);

        JsonNode toolCallsNode = message.get("tool_calls");
        if (toolCallsNode != null && toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            for (JsonNode tc : toolCallsNode) {
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("id", tc.get("id").asText());
                call.put("type", "function");
                Map<String, Object> func = new LinkedHashMap<>();
                JsonNode fn = tc.get("function");
                if (fn == null || fn.isNull()) continue;
                func.put("name", fn.get("name").asText());
                func.put("arguments", fn.get("arguments").asText());
                call.put("function", func);
                toolCalls.add(call);
            }
            result.setToolCalls(toolCalls);
        }

        log.debug("LLM 响应: content={}, toolCalls={}",
                truncateForLog(result.getContent()),
                result.getToolCalls() != null ? result.getToolCalls().size() : 0);
        return result;
    }

    /** 构建 OpenAI messages 数组 */
    private List<Map<String, Object>> buildMessagesArray(List<AgentMessage> messages) {
        List<Map<String, Object>> arr = new ArrayList<>();
        for (AgentMessage msg : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", msg.getRole());

            if ("tool".equals(msg.getRole())) {
                item.put("tool_call_id", msg.getToolCallId());
                item.put("content", msg.getContent());
            } else {
                if (msg.getContent() != null) {
                    item.put("content", msg.getContent());
                }
                if (msg.getToolCalls() != null) {
                    item.put("tool_calls", msg.getToolCalls());
                }
            }
            arr.add(item);
        }
        return arr;
    }

    private static String truncateForLog(String s) {
        if (s == null) return "null";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
