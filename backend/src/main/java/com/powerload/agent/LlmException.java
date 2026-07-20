package com.powerload.agent;

/**
 * LLM 调用异常 — 用于封装 HTTP 错误、超时、空 choices、无效 JSON 等。
 */
public class LlmException extends Exception {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
