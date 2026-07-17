package com.powerload.websocket;

import com.powerload.security.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器 — 校验 JWT Token
 * 从 query param "token" 或 "Authorization" header 提取 JWT
 * 校验失败则拒绝握手（返回 401）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtils jwtUtils;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = null;

        // 1. 尝试从 query param 获取
        if (request.getURI().getQuery() != null) {
            for (String param : request.getURI().getQuery().split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "token".equals(kv[0])) {
                    token = kv[1];
                    break;
                }
            }
        }

        // 2. 尝试从 STOMP CONNECT header "Authorization: Bearer <token>" 获取
        //    (STOMP headers 在握手时不可用，需要从 HTTP request 获取)
        if (token == null && request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            String authHeader = servletRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
            // 也检查 query param from servlet request
            if (token == null) {
                token = servletRequest.getParameter("token");
            }
        }

        if (token == null || token.isBlank()) {
            log.debug("WebSocket 握手拒绝：缺少 Token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Claims claims = jwtUtils.parseToken(token);
            if (!jwtUtils.isAccessToken(claims)) {
                log.debug("WebSocket 握手拒绝：非 Access Token");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            // 将用户信息存入 attributes 供后续使用
            attributes.put("userId", jwtUtils.getUserId(claims));
            attributes.put("username", jwtUtils.getUsername(claims));
            attributes.put("role", jwtUtils.getRole(claims));
            log.debug("WebSocket 握手成功: user={}", jwtUtils.getUsername(claims));
            return true;
        } catch (Exception e) {
            log.debug("WebSocket 握手拒绝：Token 无效 — {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
