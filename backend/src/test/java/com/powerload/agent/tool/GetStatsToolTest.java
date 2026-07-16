package com.powerload.agent.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerload.agent.ToolResult;
import com.powerload.dto.response.LoadStats;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.AlertEvent;
import com.powerload.service.AlertEventService;
import com.powerload.service.LoadDataService;
import com.powerload.service.RealtimeLoadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetStatsToolTest {

    private GetStatsTool tool;
    private RealtimeLoadService realtimeLoadService;
    private LoadDataService loadDataService;
    private AlertEventService alertEventService;

    @BeforeEach
    void setUp() {
        realtimeLoadService = mock(RealtimeLoadService.class);
        loadDataService = mock(LoadDataService.class);
        alertEventService = mock(AlertEventService.class);
        tool = new GetStatsTool(realtimeLoadService, loadDataService, alertEventService);
    }

    @Test
    void shouldReturnToolName() {
        assertEquals("get_stats", tool.name());
    }

    @Test
    void shouldReturnRealtimeLoadWhenAvailable() {
        RealtimeLoadPoint point = new RealtimeLoadPoint();
        point.setTimestamp(System.currentTimeMillis());
        point.setLoadMw(940.5f);
        point.setTemperature(28.3f);
        point.setHumidity(65f);
        point.setSource("MOCK");
        when(realtimeLoadService.getLatest()).thenReturn(point);
        when(alertEventService.query(anyInt(), anyInt(), any(), any(), any(), anyBoolean()))
                .thenReturn(new Page<>(1, 5));

        ToolResult r = tool.execute("{}");
        assertTrue(r.isSuccess());
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) r.getData();
        assertEquals(940.5f, (float) data.get("currentLoad"));
        assertEquals(28.3f, (float) data.get("temperature"));
        assertEquals(65f, (float) data.get("humidity"));
        assertEquals("MOCK", data.get("source"));
    }

    @Test
    void shouldMarkAsMockData() {
        RealtimeLoadPoint point = new RealtimeLoadPoint();
        point.setLoadMw(850f);
        point.setSource("MOCK");
        when(realtimeLoadService.getLatest()).thenReturn(point);
        when(alertEventService.query(anyInt(), anyInt(), any(), any(), any(), anyBoolean()))
                .thenReturn(new Page<>(1, 5));

        ToolResult r = tool.execute("{}");
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) r.getData();
        assertEquals("MOCK", data.get("source"));
        assertTrue(r.getMessage().contains("模拟数据"));
    }

    @Test
    void shouldReturnNullLoadWhenNoRealtimeData() {
        when(realtimeLoadService.getLatest()).thenReturn(null);
        when(alertEventService.query(anyInt(), anyInt(), any(), any(), any(), anyBoolean()))
                .thenReturn(new Page<>(1, 5));

        ToolResult r = tool.execute("{}");
        assertTrue(r.isSuccess());
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) r.getData();
        assertNull(data.get("currentLoad"));
    }

    @Test
    void shouldIncludeAlertsWhenRequested() {
        RealtimeLoadPoint point = new RealtimeLoadPoint();
        point.setLoadMw(900f);
        point.setSource("MOCK");
        when(realtimeLoadService.getLatest()).thenReturn(point);

        AlertEvent event = new AlertEvent();
        event.setId(1L);
        event.setLevel("RED");
        event.setAiAnalysis("负荷超过安全阈值");
        event.setTriggerTime(java.time.LocalDateTime.now());
        event.setCurrentValue(1100f);
        Page<AlertEvent> page = new Page<>(1, 5);
        page.setRecords(List.of(event));
        when(alertEventService.query(anyInt(), anyInt(), any(), any(), any(), anyBoolean())).thenReturn(page);

        ToolResult r = tool.execute("{\"includeAlerts\":true}");
        assertTrue(r.isSuccess());
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) r.getData();
        @SuppressWarnings("unchecked")
        var alerts = (List<Map<String, Object>>) data.get("recentAlerts");
        assertEquals(1, alerts.size());
        assertEquals("RED", alerts.get(0).get("level"));
    }

    @Test
    void shouldIncludeStatsWhenTimeRangeProvided() {
        RealtimeLoadPoint point = new RealtimeLoadPoint();
        point.setLoadMw(900f);
        point.setSource("MOCK");
        when(realtimeLoadService.getLatest()).thenReturn(point);
        when(alertEventService.query(anyInt(), anyInt(), any(), any(), any(), anyBoolean()))
                .thenReturn(new Page<>(1, 5));

        LoadStats stats = new LoadStats();
        stats.setDataPoints(10);
        stats.setPeakLoad(1000f);
        stats.setAvgLoad(850f);
        when(loadDataService.getStats(any(), any())).thenReturn(stats);

        String json = """
                {"startTime":"2026-07-15T00:00:00+08:00","endTime":"2026-07-16T00:00:00+08:00"}""";
        ToolResult r = tool.execute(json);
        assertTrue(r.isSuccess());
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) r.getData();
        @SuppressWarnings("unchecked")
        var statsMap = (Map<String, Object>) data.get("stats");
        assertEquals(1000f, (float) statsMap.get("peakLoad"));
        assertEquals(850f, (float) statsMap.get("avgLoad"));
    }

    @Test
    void shouldReturnErrorForInvalidJson() {
        ToolResult r = tool.execute("not json");
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("JSON 解析失败"));
    }
}
