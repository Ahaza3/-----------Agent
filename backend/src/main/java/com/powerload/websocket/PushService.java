package com.powerload.websocket;

import com.powerload.dto.response.ForecastResponse;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.AlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 推送服务
 *
 * <p>封装 SimpMessagingTemplate，提供三个推送通道：
 * <ul>
 *   <li>/topic/load        — 实时负荷（每秒，RealtimeLoadPoint）</li>
 *   <li>/topic/alerts       — 告警事件（触发时）</li>
 *   <li>/topic/predictions  — 预测结果更新（触发时）</li>
 * </ul>
 *
 * <p>使用原生 STOMP over WebSocket，端点为 /ws/dashboard。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 推送实时负荷（使用 RealtimeLoadPoint，epoch 毫秒时间戳）
     */
    public void pushRealtimeLoad(RealtimeLoadPoint point) {
        if (point == null) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "load_update");
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", point.getTimestamp());
        data.put("sequence", point.getSequence());
        data.put("loadMw", point.getLoadMw());
        data.put("temperature", point.getTemperature());
        data.put("humidity", point.getHumidity());
        data.put("source", point.getSource());
        payload.put("data", data);
        messagingTemplate.convertAndSend("/topic/load", payload);
    }

    /**
     * 推送告警事件
     */
    public void pushAlert(AlertEvent event) {
        if (event == null) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "alert");
        Map<String, Object> data = new HashMap<>();
        data.put("id", event.getId());
        data.put("triggerTime", event.getTriggerTime() != null ? event.getTriggerTime().toString() : null);
        data.put("level", event.getLevel());
        data.put("currentValue", event.getCurrentValue());
        data.put("thresholdValue", event.getThresholdValue());
        data.put("aiAnalysis", event.getAiAnalysis() != null ? event.getAiAnalysis() : "");
        data.put("suggestion", event.getSuggestion() != null ? event.getSuggestion() : "");
        payload.put("data", data);
        messagingTemplate.convertAndSend("/topic/alerts", payload);
        log.info("告警推送: level={}, current={}MW", event.getLevel(), event.getCurrentValue());
    }

    /**
     * 推送预测结果
     */
    public void pushPrediction(ForecastResponse forecast) {
        if (forecast == null) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "prediction_update");
        Map<String, Object> data = new HashMap<>();
        data.put("predictions", forecast.getPredictions());
        data.put("model", forecast.getModel() != null ? forecast.getModel() : "");
        data.put("forecastStartTime", forecast.getForecastStartTime() != null ? forecast.getForecastStartTime().toString() : null);
        payload.put("data", data);
        messagingTemplate.convertAndSend("/topic/predictions", payload);
        log.debug("预测推送: model={}, {} values", forecast.getModel(),
                forecast.getPredictions() != null ? forecast.getPredictions().size() : 0);
    }
}
