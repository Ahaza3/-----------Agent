package com.powerload.controller;

import com.powerload.agent.AgentService;
import com.powerload.agent.MockLlmClient;
import com.powerload.agent.LlmClient;
import com.powerload.dto.request.AgentChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * Agent 对话 SSE 控制器
 *
 * <p>POST /api/v1/agent/chat — SSE 流式对话接口</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final LlmClient llmClient;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody AgentChatRequest request) {
        String message = request.getMessage();
        if (message == null || message.isBlank()) {
            SseEmitter errEmitter = new SseEmitter(5000L);
            new Thread(() -> {
                try {
                    sendEvent(errEmitter, "error", Map.of("message", "消息不能为空"));
                    sendEvent(errEmitter, "done", Map.of("conversationId", ""));
                    errEmitter.complete();
                } catch (IOException e) {
                    errEmitter.completeWithError(e);
                }
            }).start();
            return errEmitter;
        }

        // API Key 未配置时使用 MockLlmClient，提前告知用户
        if (llmClient instanceof MockLlmClient) {
            log.info("Agent 使用 Mock 模式回答");
        }

        return agentService.chat(request.getConversationId(), message);
    }

    private void sendEvent(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }
}
