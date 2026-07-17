package com.powerload.config;

import com.powerload.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 公开端点
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/doc.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                // 数据查询 — 所有认证角色
                .requestMatchers(HttpMethod.GET, "/api/v1/data/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/predict/**").authenticated()
                // Agent
                .requestMatchers("/api/v1/agent/chat").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/agent/conversations/**").authenticated()
                // 告警查看
                .requestMatchers(HttpMethod.GET, "/api/v1/alert/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/v1/alert/events/*/read").authenticated()
                // 管理操作
                .requestMatchers("/api/v1/alert/rules/**").hasAnyRole("OPERATOR", "SYSTEM_ADMIN")
                .requestMatchers("/api/v1/demo/**").hasAnyRole("DISPATCHER", "OPERATOR", "SYSTEM_ADMIN")
                .requestMatchers("/api/v1/system/**").hasRole("SYSTEM_ADMIN")
                // 其余需要认证
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
