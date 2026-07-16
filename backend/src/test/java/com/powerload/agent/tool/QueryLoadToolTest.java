package com.powerload.agent.tool;

import com.powerload.agent.ToolResult;
import com.powerload.entity.LoadData;
import com.powerload.service.LoadDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryLoadToolTest {

    private QueryLoadTool tool;
    private LoadDataService loadDataService;

    @BeforeEach
    void setUp() {
        loadDataService = mock(LoadDataService.class);
        tool = new QueryLoadTool(loadDataService);
    }

    @Test
    void shouldReturnToolNameAndDescription() {
        assertEquals("query_load", tool.name());
        assertNotNull(tool.description());
    }

    @Test
    void shouldHaveRequiredParameters() {
        var params = tool.parameters();
        assertEquals("object", params.get("type"));
        @SuppressWarnings("unchecked")
        var required = (List<String>) params.get("required");
        assertTrue(required.contains("startTime"));
        assertTrue(required.contains("endTime"));
    }

    @Test
    void shouldReturnErrorForMissingParameters() {
        ToolResult r = tool.execute("{}");
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("缺少必填"));
    }

    @Test
    void shouldReturnErrorForInvalidJson() {
        ToolResult r = tool.execute("not json");
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("JSON 解析失败"));
    }

    @Test
    void shouldReturnErrorForStartAfterEnd() {
        String json = """
                {"startTime":"2026-07-15T12:00:00+08:00","endTime":"2026-07-15T00:00:00+08:00"}""";
        ToolResult r = tool.execute(json);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("早于"));
    }

    @Test
    void shouldReturnErrorForRangeExceeds31Days() {
        String json = """
                {"startTime":"2026-06-01T00:00:00+08:00","endTime":"2026-08-01T00:00:00+08:00"}""";
        ToolResult r = tool.execute(json);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("31"));
    }

    @Test
    void shouldReturnEmptyResultForNoData() {
        when(loadDataService.queryRange(any(), any())).thenReturn(List.of());
        String json = """
                {"startTime":"2026-07-15T00:00:00+08:00","endTime":"2026-07-15T12:00:00+08:00"}""";
        ToolResult r = tool.execute(json);
        assertTrue(r.isSuccess());
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) r.getData();
        assertEquals(0, data.get("totalPoints"));
        assertEquals(0, data.get("returnedPoints"));
    }

    @Test
    void shouldSampleWhenExceedsMaxPoints() {
        List<LoadData> raw = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            LoadData d = new LoadData();
            d.setTime(LocalDateTime.of(2026, 7, 15, 0, 0).plusHours(i));
            d.setLoadMw(800f + i);
            d.setTemperature(25f);
            d.setHumidity(60f);
            raw.add(d);
        }
        when(loadDataService.queryRange(any(), any())).thenReturn(raw);

        String json = """
                {"startTime":"2026-07-15T00:00:00+08:00","endTime":"2026-07-23T00:00:00+08:00","maxPoints":50}""";
        ToolResult r = tool.execute(json);
        assertTrue(r.isSuccess());
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) r.getData();
        assertEquals(200, data.get("totalPoints"));
        assertTrue((int) data.get("returnedPoints") <= 50);
        assertTrue((boolean) data.get("sampled"));
    }

    @Test
    void shouldReturnMockSourceIdentifier() {
        LoadData d = new LoadData();
        d.setTime(LocalDateTime.of(2026, 7, 15, 10, 0));
        d.setLoadMw(900f);
        d.setTemperature(25f);
        d.setHumidity(60f);
        when(loadDataService.queryRange(any(), any())).thenReturn(List.of(d));

        String json = """
                {"startTime":"2026-07-15T00:00:00+08:00","endTime":"2026-07-15T12:00:00+08:00"}""";
        ToolResult r = tool.execute(json);
        assertTrue(r.isSuccess());
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) r.getData();
        assertEquals("MOCK_HISTORY", data.get("source"));
    }

    @Test
    void shouldReturnChartOption() {
        LoadData d = new LoadData();
        d.setTime(LocalDateTime.of(2026, 7, 15, 10, 0));
        d.setLoadMw(900f);
        d.setTemperature(25f);
        d.setHumidity(60f);
        when(loadDataService.queryRange(any(), any())).thenReturn(List.of(d));

        String json = """
                {"startTime":"2026-07-15T00:00:00+08:00","endTime":"2026-07-15T12:00:00+08:00"}""";
        ToolResult r = tool.execute(json);
        assertNotNull(r.getChart());
    }

    @Test
    void shouldParseLocalDateTimeWithoutTimezone() {
        LoadData d = new LoadData();
        d.setTime(LocalDateTime.of(2026, 7, 15, 10, 0));
        d.setLoadMw(900f);
        d.setTemperature(25f);
        d.setHumidity(60f);
        when(loadDataService.queryRange(any(), any())).thenReturn(List.of(d));

        // No timezone offset — treated as Asia/Shanghai local
        String json = """
                {"startTime":"2026-07-15T00:00:00","endTime":"2026-07-15T12:00:00"}""";
        ToolResult r = tool.execute(json);
        assertTrue(r.isSuccess());
    }
}
