package com.powerload.alert;

import com.powerload.dto.response.AlertJudgementResult;
import com.powerload.agent.AgentMessage;
import com.powerload.agent.LlmClient;
import com.powerload.dto.response.ForecastResponse;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.AlertEvent;
import com.powerload.mapper.AlertAdviceMapper;
import com.powerload.mapper.AlertTicketMapper;
import com.powerload.service.PredictService;
import com.powerload.service.RealtimeLoadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertJudgementServiceTest {

    @Mock private AlertAdviceMapper alertAdviceMapper;
    @Mock private AlertTicketMapper alertTicketMapper;
    @Mock private RealtimeLoadService realtimeLoadService;
    @Mock private PredictService predictService;
    @Mock private LlmClient llmClient;
    @InjectMocks private AlertJudgementService service;

    private AlertEvent makeAlert(String level, float load, float threshold) {
        AlertEvent e = new AlertEvent();
        e.setId(1L);
        e.setLevel(level);
        e.setCurrentValue(load);
        e.setThresholdValue(threshold);
        return e;
    }

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(realtimeLoadService.getRecent(anyInt())).thenReturn(List.of(
            makePoint(1000, 1000f), makePoint(2000, 1010f)
        ));
        ForecastResponse f = new ForecastResponse();
        f.setPredictions(List.of(1000.0, 1050.0, 1100.0, 1150.0, 1200.0));
        f.setModel("LSTM");
        lenient().when(predictService.forecast()).thenReturn(f);
        lenient().when(alertTicketMapper.selectCount(any())).thenReturn(0L);
        lenient().when(alertAdviceMapper.selectList(any())).thenReturn(List.of());
        lenient().when(alertAdviceMapper.selectOne(any())).thenReturn(null);
        lenient().doReturn(1).when(alertAdviceMapper).insert(any(com.powerload.entity.AlertAdvice.class));
        lenient().when(llmClient.chat(anyList(), isNull())).thenReturn(AgentMessage.assistant("""
                {"decisionReason":"LLM研判：红色告警已超过阈值，需要调度员确认后建单处置。",
                "dispatcherAdvice":"请立即核实负荷趋势，确认后创建紧急工单并安排处置。",
                "operatorAdvice":"请核查数据采集链路、阈值配置和实时推送状态。"}
                """));
    }

    private RealtimeLoadPoint makePoint(long ts, float load) {
        RealtimeLoadPoint p = new RealtimeLoadPoint();
        p.setTimestamp(ts); p.setSequence(ts); p.setLoadMw(load); p.setSource("MOCK");
        return p;
    }

    @Test
    void redAlertWithoutExistingTicketShouldSuggestManualTicket() {
        AlertJudgementResult r = service.judge(makeAlert("RED", 1200f, 1100f));
        assertTrue(r.getShouldCreateTicket());
        assertFalse(r.getAutoCreateTicket());
        assertEquals("URGENT", r.getRecommendedPriority());
        assertEquals("LLM_AGENT", r.getSource());
    }

    @Test
    void redAlertWithExistingTicketShouldNotDuplicate() {
        when(alertTicketMapper.selectCount(any())).thenReturn(1L);
        AlertJudgementResult r = service.judge(makeAlert("RED", 1200f, 1100f));
        assertFalse(r.getShouldCreateTicket());
        assertFalse(r.getAutoCreateTicket());
    }

    @Test
    void orangeAlertShouldNotAutoCreate() {
        AlertJudgementResult r = service.judge(makeAlert("ORANGE", 1100f, 1000f));
        assertFalse(r.getAutoCreateTicket());
    }

    @Test
    void yellowAlertShouldNotCreateByDefault() {
        when(predictService.forecast()).thenReturn(null);
        AlertJudgementResult r = service.judge(makeAlert("YELLOW", 950f, 1000f));
        assertFalse(r.getShouldCreateTicket());
        assertFalse(r.getAutoCreateTicket());
    }

    @Test
    void yellowAlertWithHighForecastShouldSuggestCreate() {
        // forecast 1200 > threshold * 1.1 = 1100
        AlertJudgementResult r = service.judge(makeAlert("YELLOW", 980f, 1000f));
        assertTrue(r.getShouldCreateTicket());
        assertFalse(r.getAutoCreateTicket());
    }

    @Test
    void detectTrendShouldWork() {
        when(realtimeLoadService.getRecent(5)).thenReturn(List.of(
            makePoint(1000, 1000f), makePoint(2000, 1025f) // +2.5% → RISING
        ));
        assertEquals("RISING", service.detectTrend(5));
    }

    @Test
    void detectTrendStable() {
        when(realtimeLoadService.getRecent(5)).thenReturn(List.of(
            makePoint(1000, 1000f), makePoint(2000, 1005f) // +0.5% → STABLE
        ));
        assertEquals("STABLE", service.detectTrend(5));
    }

    @Test
    void detectTrendUnknownWhenNoData() {
        when(realtimeLoadService.getRecent(5)).thenReturn(List.of());
        assertEquals("UNKNOWN", service.detectTrend(5));
    }
}
