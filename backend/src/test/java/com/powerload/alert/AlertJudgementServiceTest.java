package com.powerload.alert;

import com.powerload.dto.response.AlertJudgementResult;
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
    void setUp() {
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
    }

    private RealtimeLoadPoint makePoint(long ts, float load) {
        RealtimeLoadPoint p = new RealtimeLoadPoint();
        p.setTimestamp(ts); p.setSequence(ts); p.setLoadMw(load); p.setSource("MOCK");
        return p;
    }

    @Test
    void redAlertWithoutExistingTicketShouldAutoCreate() {
        AlertJudgementResult r = service.judge(makeAlert("RED", 1200f, 1100f));
        assertTrue(r.getShouldCreateTicket());
        assertTrue(r.getAutoCreateTicket());
        assertEquals("URGENT", r.getRecommendedPriority());
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
