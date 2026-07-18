package com.powerload.agent.tool;

import com.powerload.agent.ToolResult;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.AlertRule;
import com.powerload.entity.PredictionResult;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.mapper.AlertRuleMapper;
import com.powerload.mapper.AlertTicketMapper;
import com.powerload.mapper.PredictionResultMapper;
import com.powerload.service.RealtimeLoadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QueryDispatchRiskBriefToolTest {

    private RealtimeLoadService realtimeLoadService;
    private PredictionResultMapper predictionResultMapper;
    private AlertRuleMapper alertRuleMapper;
    private AlertEventMapper alertEventMapper;
    private AlertTicketMapper ticketMapper;
    private QueryDispatchRiskBriefTool tool;

    @BeforeEach
    void setUp() {
        realtimeLoadService = mock(RealtimeLoadService.class);
        predictionResultMapper = mock(PredictionResultMapper.class);
        alertRuleMapper = mock(AlertRuleMapper.class);
        alertEventMapper = mock(AlertEventMapper.class);
        ticketMapper = mock(AlertTicketMapper.class);
        tool = new QueryDispatchRiskBriefTool(realtimeLoadService, predictionResultMapper,
                alertRuleMapper, alertEventMapper, ticketMapper);
    }

    @Test
    void shouldRecommendPrewarningTicketWhenForecastPeakCrossesOrangeThreshold() {
        RealtimeLoadPoint latestLoad = new RealtimeLoadPoint();
        latestLoad.setLoadMw(940f);
        when(realtimeLoadService.getLatest()).thenReturn(latestLoad);

        LocalDateTime batchTime = LocalDateTime.of(2026, 7, 18, 18, 0);
        PredictionResult latestBatchMarker = new PredictionResult();
        latestBatchMarker.setCreatedAt(batchTime);
        when(predictionResultMapper.selectOne(any())).thenReturn(latestBatchMarker);
        when(predictionResultMapper.selectList(any())).thenReturn(List.of(
                prediction(batchTime, 1, 980f),
                prediction(batchTime, 2, 1140f),
                prediction(batchTime, 3, 1000f)
        ));

        AlertRule rule = new AlertRule();
        rule.setConfig("{\"threshold\":1100,\"yellowRatio\":0.9,\"orangeRatio\":1.0,\"redRatio\":1.1}");
        when(alertRuleMapper.selectOne(any())).thenReturn(rule);
        when(alertEventMapper.selectCount(any())).thenReturn(2L);
        when(ticketMapper.selectCount(any()))
                .thenReturn(4L)
                .thenReturn(1L);

        ToolResult result = tool.execute("{}");

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("MOCK_OPERATION_BRIEF", data.get("source"));
        assertEquals(940f, data.get("currentLoadMw"));
        assertEquals(1140f, data.get("forecastPeakLoadMw"));
        assertEquals("ORANGE", data.get("forecastRiskLevel"));
        assertEquals(true, data.get("prewarningTicketRecommended"));
        assertEquals(2L, data.get("unreadAlertCount"));
        assertEquals(4L, data.get("openTicketCount"));
        assertEquals(1L, data.get("openPrewarningTicketCount"));
    }

    private PredictionResult prediction(LocalDateTime batchTime, int hourOffset, float load) {
        PredictionResult result = new PredictionResult();
        result.setCreatedAt(batchTime);
        result.setPredictTime(batchTime.plusHours(hourOffset));
        result.setPredictedLoad(load);
        return result;
    }
}
