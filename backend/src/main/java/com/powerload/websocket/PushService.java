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
        data.put("nodeId", event.getNodeId());
        data.put("rootEventId", event.getRootEventId());
        data.put("impactLoadMw", event.getImpactLoadMw());
        data.put("triggerTime", event.getTriggerTime() != null ? event.getTriggerTime().toString() : null);
        data.put("level", event.getLevel());
        data.put("type", event.getType());
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
        data.put("nodeId", forecast.getNodeId());
        data.put("source", forecast.getSource() != null ? forecast.getSource() : "ROOT_REGION");
        data.put("predictions", forecast.getPredictions());
        data.put("model", forecast.getModel() != null ? forecast.getModel() : "");
        data.put("forecastStartTime", forecast.getForecastStartTime() != null ? forecast.getForecastStartTime().toString() : null);
        data.put("lowerBounds", forecast.getLowerBounds());
        data.put("upperBounds", forecast.getUpperBounds());
        data.put("intervalSource", forecast.getIntervalSource());
        data.put("modelVersionId", forecast.getModelVersionId());
        data.put("futureWeatherAvailable", forecast.isFutureWeatherAvailable());
        data.put("weatherSource", forecast.getWeatherSource());
        data.put("futureWeatherApplied", forecast.isFutureWeatherApplied());
        data.put("futureWeatherFallback", forecast.isFutureWeatherFallback());
        payload.put("data", data);
        messagingTemplate.convertAndSend("/topic/predictions", payload);
        log.debug("预测推送: model={}, {} values", forecast.getModel(),
                forecast.getPredictions() != null ? forecast.getPredictions().size() : 0);
    }

    /** 通用推送：向指定 topic 发送任意 payload */
    public void pushToTopic(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.debug("WebSocket push failed: {}", e.getMessage());
        }
    }
}
