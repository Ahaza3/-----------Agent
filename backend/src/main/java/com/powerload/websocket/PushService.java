package com.powerload.websocket;

import com.powerload.dto.response.ForecastResponse;
import com.powerload.entity.AlertEvent;
import com.powerload.entity.LoadData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * WebSocket 推送服务
 *
 * <p>封装 SimpMessagingTemplate，提供三个推送通道：
 * <ul>
 *   <li>/topic/load        — 实时负荷数据（每秒）</li>
 *   <li>/topic/alerts       — 告警事件（触发时）</li>
 *   <li>/topic/predictions  — 预测结果更新（每小时）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 推送实时负荷
     */
    public void pushLoad(LoadData data) {
        if (data == null) return;
        Map<String, Object> payload = Map.of(
                "type", "load_update",
                "data", Map.of(
                        "time", data.getTime() != null ? data.getTime().toString() : null,
                        "loadMw", data.getLoadMw(),
                        "temperature", data.getTemperature(),
                        "humidity", data.getHumidity()
                )
        );
        messagingTemplate.convertAndSend("/topic/load", payload);
    }

    /**
     * 推送告警事件
     */
    public void pushAlert(AlertEvent event) {
        if (event == null) return;
        Map<String, Object> payload = Map.of(
                "type", "alert",
                "data", Map.of(
                        "id", event.getId(),
                        "triggerTime", event.getTriggerTime() != null ? event.getTriggerTime().toString() : null,
                        "level", event.getLevel(),
                        "currentValue", event.getCurrentValue(),
                        "thresholdValue", event.getThresholdValue(),
                        "aiAnalysis", event.getAiAnalysis() != null ? event.getAiAnalysis() : "",
                        "suggestion", event.getSuggestion() != null ? event.getSuggestion() : ""
                )
        );
        messagingTemplate.convertAndSend("/topic/alerts", payload);
        log.info("告警推送: level={}, current={}MW", event.getLevel(), event.getCurrentValue());
    }

    /**
     * 推送预测结果
     */
    public void pushPrediction(ForecastResponse forecast) {
        if (forecast == null) return;
        Map<String, Object> payload = Map.of(
                "type", "prediction_update",
                "data", Map.of(
                        "predictions", forecast.getPredictions(),
                        "model", forecast.getModel() != null ? forecast.getModel() : ""
                )
        );
        messagingTemplate.convertAndSend("/topic/predictions", payload);
        log.debug("预测推送: model={}, {} values", forecast.getModel(),
                forecast.getPredictions() != null ? forecast.getPredictions().size() : 0);
    }
}
