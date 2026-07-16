package com.powerload.scheduler;

import com.powerload.alert.AlertTemplate;
import com.powerload.alert.ThresholdDetector;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.AlertEvent;
import com.powerload.entity.AlertRule;
import com.powerload.service.AlertEventService;
import com.powerload.service.AlertRuleService;
import com.powerload.service.RealtimeLoadService;
import com.powerload.websocket.PushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlertSchedulerTest {

    private RealtimeLoadService realtimeLoadService;
    private AlertEventService alertEventService;
    private PushService pushService;
    private AlertScheduler scheduler;

    @BeforeEach
    void setUp() {
        realtimeLoadService = mock(RealtimeLoadService.class);
        AlertRuleService alertRuleService = mock(AlertRuleService.class);
        alertEventService = mock(AlertEventService.class);
        pushService = mock(PushService.class);

        AlertRule rule = new AlertRule();
        rule.setId(1L);
        rule.setConfig("{\"threshold\":1100,\"yellowRatio\":0.9,\"orangeRatio\":1.0,"
                + "\"redRatio\":1.1,\"coolingTime\":0}");
        when(alertRuleService.listActive()).thenReturn(List.of(rule));
        when(alertEventService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        scheduler = new AlertScheduler(
                realtimeLoadService,
                alertRuleService,
                alertEventService,
                new ThresholdDetector(),
                new AlertTemplate(),
                pushService);
    }

    @Test
    void triggersOnlyOnEscalationAndAllowsNewCycleAfterRecovery() {
        checkAt(940);  // safe
        checkAt(1020); // yellow
        checkAt(1140); // orange
        checkAt(1240); // red
        checkAt(1140); // descending: no repeated orange
        checkAt(940);  // fully recovered
        checkAt(1020); // new cycle: yellow again

        ArgumentCaptor<AlertEvent> events = ArgumentCaptor.forClass(AlertEvent.class);
        verify(alertEventService, times(4)).save(events.capture());
        verify(pushService, times(4)).pushAlert(any(AlertEvent.class));
        assertEquals(List.of("YELLOW", "ORANGE", "RED", "YELLOW"),
                events.getAllValues().stream().map(AlertEvent::getLevel).toList());
    }

    private void checkAt(float load) {
        RealtimeLoadPoint point = new RealtimeLoadPoint();
        point.setLoadMw(load);
        when(realtimeLoadService.getLatest()).thenReturn(point);
        scheduler.checkAlerts();
    }
}
