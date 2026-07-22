package com.powerload.agent;

import com.powerload.security.SysUserPrincipal;
import com.powerload.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 业务服务 — 管理 SSE 会话、Conversation 持久化和 AgentCore 调用。
 *
 * <p>不直接依赖 HTTP 层，可被 Controller 和测试复用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final int MAX_HISTORY_MESSAGES = 10;
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final AgentCore agentCore;
    private final ConversationService conversationService;

    /**
     * 创建 SSE 流式对话。
     *
     * @param conversationId 对话 ID（null 时生成新 UUID）
     * @param userMessage    用户消息
     * @return SseEmitter
     */
    public SseEmitter chat(String conversationId, String userMessage, SysUserPrincipal user) {
        String convId = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : conversationId;

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 异步处理，不阻塞请求线程
        new Thread(() -> {
            try {
                // 加载历史
                List<AgentMessage> history = conversationService.loadHistory(convId, user, MAX_HISTORY_MESSAGES);

                // 保存用户消息
                conversationService.saveMessage(convId, user, "user", userMessage, null);

                // 执行 AgentCore
                AgentCore.AgentResponse response = agentCore.run(userMessage, history,
                        msg -> {
                            try {
                                sendEvent(emitter, "thinking", Map.of("content", msg));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }, user);

                if (!response.isSuccess()) {
                    sendEvent(emitter, "error", Map.of("message", response.getContent()));
                    // 保存错误作为 assistant 消息
                    conversationService.saveMessage(convId, user, "assistant", response.getContent(), null);
                } else {
                    // Send the complete Markdown payload once so formatting-sensitive newlines stay intact.
                    String text = response.getContent();
                    if (text != null && !text.isBlank()) {
                        sendEvent(emitter, "text", Map.of("content", text));
                    }

                    // 发送图表
                    if (response.getChart() != null) {
                        sendEvent(emitter, "chart", Map.of("option", response.getChart()));
                    }

                    // 保存 assistant 最终回答
                    conversationService.saveMessage(convId, user, "assistant", text, null, response.getChart());
                }

                // 发送 done
                Map<String, Object> done = new java.util.LinkedHashMap<>();
                done.put("conversationId", convId);
                done.put("provenance", response.getProvenance());
                sendEvent(emitter, "done", done);
                emitter.complete();

            } catch (Exception e) {
                log.error("Agent SSE 异常", e);
                try {
                    sendEvent(emitter, "error", Map.of("message", "服务异常: " + e.getMessage()));
                    sendEvent(emitter, "done", Map.of("conversationId", convId));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            } finally {
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        }, "agent-sse-" + convId.substring(0, Math.min(8, convId.length()))).start();

        // 客户端断开时回调
        emitter.onCompletion(() -> log.debug("SSE 完成: {}", convId));
        emitter.onTimeout(() -> log.debug("SSE 超时: {}", convId));
        emitter.onError(ex -> log.debug("SSE 客户端断开: {} {}", convId, ex.getMessage()));

        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String event, Object data) throws IOException {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            log.debug("SSE 发送失败（客户端可能已断开）: {}", e.getMessage());
            throw e;
        }
    }
}
