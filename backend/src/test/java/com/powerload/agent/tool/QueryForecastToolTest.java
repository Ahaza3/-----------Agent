package com.powerload.agent.tool;

import com.powerload.agent.ToolResult;
import com.powerload.entity.PredictionResult;
import com.powerload.mapper.PredictionResultMapper;
import com.powerload.service.PredictService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QueryForecastToolTest {

    private PredictionResultMapper predictionResultMapper;
    private PredictService predictService;
    private QueryForecastTool tool;

    @BeforeEach
    void setUp() {
        predictionResultMapper = mock(PredictionResultMapper.class);
        predictService = mock(PredictService.class);
        tool = new QueryForecastTool(predictionResultMapper, predictService);
    }

    @Test
    void shouldReturnToolNameAndSchema() {
        assertEquals("query_forecast", tool.name());
        assertNotNull(tool.description());
        assertTrue(tool.description().contains("MOCK_FORECAST"));
        assertEquals("object", tool.parameters().get("type"));
    }

    @Test
    void shouldReturnErrorWhenTableIsEmptyAndFlaskFails() {
        when(predictionResultMapper.selectOne(any())).thenReturn(null);
        when(predictService.forecast()).thenThrow(new RuntimeException("Flask not available"));

        ToolResult r = tool.execute("{}");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("预测"));
    }

    @Test
    void shouldReturnLatestBatch() {
        LocalDateTime batchTime = LocalDateTime.of(2026, 7, 16, 18, 0, 0);

        // First query returns latest record
        PredictionResult latest = new PredictionResult();
        latest.setCreatedAt(batchTime);
        when(predictionResultMapper.selectOne(any())).thenReturn(latest);

        // Second query returns the full batch
        List<PredictionResult> batch = new java.util.ArrayList<>();
        for (int i = 0; i < 24; i++) {
            PredictionResult pr = new PredictionResult();
            pr.setPredictTime(LocalDateTime.of(2026, 7, 16, 19, 0).plusHours(i));
            pr.setPredictedLoad(900f + i * 5f);
            pr.setCreatedAt(batchTime);
            batch.add(pr);
        }
        when(predictionResultMapper.selectList(any())).thenReturn(batch);

        ToolResult r = tool.execute("{}");

        assertTrue(r.isSuccess());
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) r.getData();
        assertEquals("MOCK_FORECAST", data.get("source"));
        assertEquals("LSTM", data.get("model"));
        assertEquals(24, data.get("totalPoints"));
        assertNotNull(r.getChart());
        assertTrue(r.getMessage().contains("模拟预测数据"));
    }

    @Test
    void shouldAutoGenerateWhenTableEmpty() {
        PredictionResult latest = new PredictionResult();
        latest.setCreatedAt(LocalDateTime.now());
        when(predictionResultMapper.selectOne(any()))
                .thenReturn(null)   // first call: empty
                .thenReturn(latest); // second call: has data

        // predictService.forecast() returns ForecastResponse (non-void)
        com.powerload.dto.response.ForecastResponse mockResp =
                new com.powerload.dto.response.ForecastResponse();
        mockResp.setPredictions(List.of(900.0));
        mockResp.setModel("LSTM");
        when(predictService.forecast()).thenReturn(mockResp);

        List<PredictionResult> batch = new java.util.ArrayList<>();
        for (int i = 0; i < 24; i++) {
            PredictionResult pr = new PredictionResult();
            pr.setPredictTime(LocalDateTime.now().plusHours(i + 1).withMinute(0).withSecond(0));
            pr.setPredictedLoad(900f + i * 5f);
            pr.setCreatedAt(latest.getCreatedAt());
            batch.add(pr);
        }
        when(predictionResultMapper.selectList(any())).thenReturn(batch);

        ToolResult r = tool.execute("{}");

        assertTrue(r.isSuccess());
        verify(predictService, times(1)).forecast();
    }

    @Test
    void shouldReturnErrorForFlaskFailure() {
        when(predictionResultMapper.selectOne(any())).thenReturn(null);
        when(predictService.forecast()).thenThrow(new RuntimeException("Flask not reachable"));

        ToolResult r = tool.execute("{}");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("预测"));
    }
}
