package com.powerload.scheduler;

import com.powerload.alert.AlertJudgementService;
import com.powerload.alert.AlertRuntimeResult;
import com.powerload.alert.AlertRuntimeStateService;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.AlertEvent;
import com.powerload.entity.AlertRule;
import com.powerload.service.AlertRuleService;
import com.powerload.service.RealtimeLoadService;
import com.powerload.websocket.PushService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlertSchedulerTest {

    private RealtimeLoadService realtimeLoadService;
    private PushService pushService;
    private AlertScheduler scheduler;

    @BeforeEach
    void setUp() {
        realtimeLoadService = mock(RealtimeLoadService.class);
        AlertRuleService alertRuleService = mock(AlertRuleService.class);
        AlertRuntimeStateService runtimeStateService = mock(AlertRuntimeStateService.class);
        pushService = mock(PushService.class);

        AlertRule rule = new AlertRule();
        rule.setId(1L);
        rule.setConfig("{\"threshold\":1100,\"yellowRatio\":0.9,\"orangeRatio\":1.0,"
                + "\"redRatio\":1.1,\"coolingTime\":0}");
        when(alertRuleService.listActive()).thenReturn(List.of(rule));
        when(runtimeStateService.evaluate(any(), any())).thenAnswer(invocation -> {
            RealtimeLoadPoint point = invocation.getArgument(1);
            if (point.getLoadMw() < 1000) return AlertRuntimeResult.unchanged();
            AlertEvent event = new AlertEvent();
            event.setLevel(point.getLoadMw() >= 1200 ? "RED" : point.getLoadMw() >= 1100 ? "ORANGE" : "YELLOW");
            return new AlertRuntimeResult(event);
        });

        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        AlertJudgementService judgementService = mock(AlertJudgementService.class);
        scheduler = new AlertScheduler(
                realtimeLoadService,
                alertRuleService,
                runtimeStateService,
                pushService,
                judgementService,
                eventPublisher);
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
        verify(pushService, times(5)).pushAlert(events.capture());
        assertEquals(List.of("YELLOW", "ORANGE", "RED", "ORANGE", "YELLOW"),
                events.getAllValues().stream().map(AlertEvent::getLevel).toList());
    }

    private void checkAt(float load) {
        RealtimeLoadPoint point = new RealtimeLoadPoint();
        point.setLoadMw(load);
        when(realtimeLoadService.getLatestForAlert()).thenReturn(point);
        scheduler.checkAlerts();
    }
}
