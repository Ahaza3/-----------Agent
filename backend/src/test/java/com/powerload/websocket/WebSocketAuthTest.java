package com.powerload.websocket;

import com.powerload.security.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private WebSocketHandler webSocketHandler;

    @InjectMocks
    private JwtHandshakeInterceptor interceptor;

    @Test
    void shouldRejectHandshakeWithoutToken() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create("/ws/dashboard"));

        boolean result = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);

        assertFalse(result);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectHandshakeWithInvalidToken() {
        ServletServerHttpRequest request = mock(ServletServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create("/ws/dashboard"));
        when(request.getServletRequest()).thenReturn(servletRequest);
        when(servletRequest.getHeader("Authorization")).thenReturn("Bearer invalid_token");
        when(jwtUtils.parseToken("invalid_token")).thenThrow(new RuntimeException("Token invalid"));

        boolean result = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);

        assertFalse(result);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAcceptHandshakeWithValidToken() {
        ServletServerHttpRequest request = mock(ServletServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create("/ws/dashboard"));
        when(request.getServletRequest()).thenReturn(servletRequest);
        when(servletRequest.getHeader("Authorization")).thenReturn("Bearer valid_token");

        // 使用 mock claims 避免依赖真正的 Claims 类型
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(jwtUtils.parseToken("valid_token")).thenReturn(claims);
        when(jwtUtils.isAccessToken(claims)).thenReturn(true);
        when(jwtUtils.getUserId(claims)).thenReturn(1L);
        when(jwtUtils.getUsername(claims)).thenReturn("dispatcher");
        when(jwtUtils.getRole(claims)).thenReturn("DISPATCHER");

        boolean result = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);

        assertTrue(result);
        assertEquals(1L, attributes.get("userId"));
        assertEquals("dispatcher", attributes.get("username"));
        assertEquals("DISPATCHER", attributes.get("role"));
    }

    @Test
    void shouldRejectNonAccessToken() {
        ServletServerHttpRequest request = mock(ServletServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create("/ws/dashboard"));
        when(request.getServletRequest()).thenReturn(servletRequest);
        when(servletRequest.getHeader("Authorization")).thenReturn("Bearer refresh_token");

        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(jwtUtils.parseToken("refresh_token")).thenReturn(claims);
        when(jwtUtils.isAccessToken(claims)).thenReturn(false); // 拒绝 Refresh Token

        boolean result = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);

        assertFalse(result);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldExtractTokenFromQueryParam() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create("/ws/dashboard?token=query_token"));

        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(jwtUtils.parseToken("query_token")).thenReturn(claims);
        when(jwtUtils.isAccessToken(claims)).thenReturn(true);
        when(jwtUtils.getUserId(claims)).thenReturn(2L);
        when(jwtUtils.getUsername(claims)).thenReturn("operator");
        when(jwtUtils.getRole(claims)).thenReturn("OPERATOR");

        boolean result = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);

        assertTrue(result);
        assertEquals(2L, attributes.get("userId"));
        assertEquals("OPERATOR", attributes.get("role"));
    }
}
