package com.powerload.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.common.GridTopologyConstants;
import com.powerload.entity.AlertEvent;
import com.powerload.entity.PredictionResult;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.mapper.PredictionResultMapper;
import com.powerload.ml.FlaskInferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemHealthService {

    private final DataSource dataSource;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;
    private final FlaskInferenceService flaskService;
    private final PredictionResultMapper predictionResultMapper;
    private final AlertEventMapper alertEventMapper;

    @Value("${llm.api.key:}")
    private String llmApiKey;

    @Value("${llm.api.model:deepseek-chat}")
    private String llmModel;

    @Value("${spring.data.redis.host:}")
    private String redisHost;

    public Map<String, Object> check() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("timestamp", LocalDateTime.now().toString());

        // Spring Boot
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        health.put("status", "UP");
        health.put("uptimeSeconds", uptime);

        // MySQL
        health.put("mysql", checkMysql());

        // Redis
        health.put("redis", checkRedis());

        // Flask 及当前实际加载的推理模型
        Map<String, Object> flaskHealth = flaskService.getHealth();
        health.put("flask", Boolean.TRUE.equals(flaskHealth.get("healthy")) ? "UP" : "DOWN");
        health.put("flaskModel", flaskHealth.getOrDefault("model_type", "UNKNOWN"));

        // LLM
        health.put("llm", Map.of(
                "configured", !llmApiKey.isBlank(),
                "model", llmModel));

        // Recent predictions
        var lastPred = predictionResultMapper.selectOne(
                new LambdaQueryWrapper<PredictionResult>()
                        .eq(PredictionResult::getNodeId, GridTopologyConstants.ROOT_NODE_ID)
                        .orderByDesc(PredictionResult::getCreatedAt).last("LIMIT 1"));
        health.put("lastPrediction", lastPred != null ? lastPred.getCreatedAt().toString() : "NONE");

        // Recent alerts
        var lastAlert = alertEventMapper.selectOne(
                new LambdaQueryWrapper<AlertEvent>()
                        .orderByDesc(AlertEvent::getCreatedAt).last("LIMIT 1"));
        health.put("lastAlert", lastAlert != null ? lastAlert.getCreatedAt().toString() : "NONE");

        health.put("websocket", Map.of("endpoint", "/ws/dashboard"));
        return health;
    }

    private String checkMysql() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN (" + e.getMessage() + ")";
        }
    }

    private String checkRedis() {
        if (redisHost.isBlank()) {
            return "DISABLED";
        }
        RedisConnectionFactory redisConnectionFactory = redisConnectionFactoryProvider.getIfAvailable();
        if (redisConnectionFactory == null) {
            return "DISABLED";
        }
        try (var connection = redisConnectionFactory.getConnection()) {
            String response = connection.ping();
            return "PONG".equalsIgnoreCase(response) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN (" + e.getMessage() + ")";
        }
    }
}
