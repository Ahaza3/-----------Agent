package com.powerload.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置 — STOMP over SockJS
 *
 * <p>端点: /ws/dashboard（原生 WebSocket，无 SockJS 包装）
 * 订阅主题: /topic/load（实时负荷）/ /topic/alerts（告警）/ /topic/predictions（预测）</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 客户端订阅前缀（服务端 → 客户端广播）
        registry.enableSimpleBroker("/topic");
        // 客户端发送前缀（暂不使用）
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/dashboard")
                .setAllowedOriginPatterns("*");
    }
}
