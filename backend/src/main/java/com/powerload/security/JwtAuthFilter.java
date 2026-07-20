package com.powerload.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            Claims claims = jwtUtils.parseToken(token);
            if (!jwtUtils.isAccessToken(claims)) {
                chain.doFilter(request, response);
                return;
            }
            String role = jwtUtils.getRole(claims);
            Long userId = jwtUtils.getUserId(claims);
            String username = jwtUtils.getUsername(claims);

            SysUserPrincipal principal = new SysUserPrincipal(userId, username, role);
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (ExpiredJwtException e) {
            log.debug("Token 已过期");
        } catch (MalformedJwtException | SignatureException e) {
            log.debug("Token 无效: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Token 解析异常", e);
        }
        chain.doFilter(request, response);
    }
}
