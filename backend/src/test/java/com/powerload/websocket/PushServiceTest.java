package com.powerload.websocket;

import com.powerload.dto.response.ForecastResponse;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.AlertEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PushService 单元测试 — 验证 null 安全处理和消息格式
 */
@ExtendWith(MockitoExtension.class)
class PushServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private PushService pushService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    @Test
    @DisplayName("5-1. RealtimeLoadPoint 推送 — 正常负载")
    void testPushRealtimeLoad_normal() {
        RealtimeLoadPoint point = new RealtimeLoadPoint();
        point.setTimestamp(1721000000000L);
        point.setSequence(100);
        point.setLoadMw(850.5f);
        point.setTemperature(28.3f);
        point.setHumidity(65.0f);
        point.setSource("MOCK");

        pushService.pushRealtimeLoad(point);

        verify(messagingTemplate).convertAndSend(eq("/topic/load"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals("load_update", payload.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        assertEquals(1721000000000L, data.get("timestamp"));
        assertEquals(100L, data.get("sequence"));
        assertEquals(850.5f, ((Number) data.get("loadMw")).floatValue(), 0.01);
        assertEquals(28.3f, ((Number) data.get("temperature")).floatValue(), 0.01);
        assertEquals(65.0f, ((Number) data.get("humidity")).floatValue(), 0.01);
        assertEquals("MOCK", data.get("source"));
    }

    @Test
    @DisplayName("5-2. RealtimeLoadPoint 推送 — 温湿度为 null 时不丢失消息")
    void testPushRealtimeLoad_nullTemperatureAndHumidity() {
        RealtimeLoadPoint point = new RealtimeLoadPoint();
        point.setTimestamp(1721000000000L);
        point.setSequence(101);
        point.setLoadMw(800f);
        point.setTemperature(null);
        point.setHumidity(null);
        point.setSource("MOCK");

        // 不应抛出 NullPointerException
        assertDoesNotThrow(() -> pushService.pushRealtimeLoad(point));

        verify(messagingTemplate).convertAndSend(eq("/topic/load"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertNotNull(payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        assertNull(data.get("temperature"));
        assertNull(data.get("humidity"));
        assertEquals(800f, ((Number) data.get("loadMw")).floatValue(), 0.01);
    }

    @Test
    @DisplayName("5-3. pushRealtimeLoad(null) 不抛异常")
    void testPushRealtimeLoad_null() {
        assertDoesNotThrow(() -> pushService.pushRealtimeLoad(null));
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("5-4. Alert 推送 — 可空字段不丢失消息")
    void testPushAlert_withNulls() {
        AlertEvent event = new AlertEvent();
        event.setId(1L);
        event.setTriggerTime(null); // 可为空
        event.setLevel("RED");
        event.setCurrentValue(1200f);
        event.setThresholdValue(1000f);
        event.setAiAnalysis(null);  // 可为空
        event.setSuggestion(null);  // 可为空

        assertDoesNotThrow(() -> pushService.pushAlert(event));
        verify(messagingTemplate).convertAndSend(eq("/topic/alerts"), any(Map.class));
    }

    @Test
    @DisplayName("5-5. Forecast 推送 — model 为 null 不丢失消息")
    void testPushPrediction_withNullModel() {
        ForecastResponse forecast = new ForecastResponse();
        forecast.setPredictions(Collections.singletonList(800.0));
        forecast.setModel(null);

        assertDoesNotThrow(() -> pushService.pushPrediction(forecast));
        verify(messagingTemplate).convertAndSend(eq("/topic/predictions"), any(Map.class));
    }
}
