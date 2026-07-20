package com.powerload.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flask 推理服务 HTTP 客户端
 *
 * <p>通过 OkHttp 调用 Python Flask 推理服务（:5000），
 * 发送原始负荷数据，获取未来 24h 预测。</p>
 */
@Slf4j
@Service
public class FlaskInferenceService {

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public FlaskInferenceService(
            @Value("${ml.service.url:http://localhost:5000}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    /**
     * 请求预测：发送原始数据行，返回 24h 预测序列
     *
     * @param rawData 原始数据行（每行含 time/load_mw/temperature/humidity/is_holiday/hour/day_of_week/month）
     * @return 24 个预测值 (MW)
     */
    @SuppressWarnings("unchecked")
    public ForecastResult forecast(List<Map<String, Object>> rawData) {
        return forecast(rawData, List.of());
    }

    @SuppressWarnings("unchecked")
    public ForecastResult forecast(
            List<Map<String, Object>> rawData,
            List<Map<String, Object>> futureWeather) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("data", rawData);
            if (futureWeather != null && !futureWeather.isEmpty()) {
                body.put("future_weather", futureWeather);
            }
            String json = objectMapper.writeValueAsString(body);

            Request request = new Request.Builder()
                    .url(baseUrl + "/predict/forecast")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "{}";
                if (!response.isSuccessful()) {
                    log.error("Flask 推理失败: HTTP {} {}", response.code(), responseBody);
                    throw new RuntimeException("Flask 推理失败: " + responseBody);
                }

                Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                List<Double> predictions = (List<Double>) result.get("predictions");
                Object modelValue = result.get("model");
                String model = modelValue == null ? "" : modelValue.toString();
                return new ForecastResult(predictions, model);
            }
        } catch (IOException e) {
            log.error("调用 Flask 推理服务异常", e);
            throw new RuntimeException("Flask 推理服务不可用: " + e.getMessage(), e);
        }
    }

    public record ForecastResult(List<Double> predictions, String model) {
    }

    /**
     * 健康检查
     */
    public boolean isHealthy() {
        return Boolean.TRUE.equals(getHealth().get("healthy"));
    }

    /**
     * 获取推理服务健康状态及当前实际加载的模型。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getHealth() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/health")
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return Map.of("healthy", false);
                }
                String responseBody = response.body() != null ? response.body().string() : "{}";
                Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                result.put("healthy", true);
                return result;
            }
        } catch (Exception e) {
            return Map.of("healthy", false);
        }
    }
}
