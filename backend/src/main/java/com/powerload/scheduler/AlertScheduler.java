package com.powerload.scheduler;

import com.powerload.alert.AlertCreatedEvent;
import com.powerload.alert.AlertJudgementService;
import com.powerload.alert.AlertRuntimeResult;
import com.powerload.alert.AlertRuntimeStateService;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.AlertRule;
import com.powerload.service.AlertRuleService;
import com.powerload.service.RealtimeLoadService;
import com.powerload.websocket.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Evaluates only P0-04 eligible telemetry; lifecycle state itself is stored by AlertRuntimeStateService. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertScheduler {
    private final RealtimeLoadService realtimeLoadService;
    private final AlertRuleService alertRuleService;
    private final AlertRuntimeStateService runtimeStateService;
    private final PushService pushService;
    private final AlertJudgementService alertJudgementService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedRate = 1_000)
    public void checkAlerts() {
        RealtimeLoadPoint latest = realtimeLoadService.getLatestForAlert();
        if (latest == null) return;
        for (AlertRule rule : alertRuleService.listActive()) {
            try {
                AlertRuntimeResult result = runtimeStateService.evaluate(rule, latest);
                if (result.getCreatedEvent() != null) publishPersisted(result.getCreatedEvent());
            } catch (Exception e) {
                log.error("ALERT_STATE_DEGRADED state evaluation failed: ruleId={}", rule.getId(), e);
            }
        }
    }

    private void publishPersisted(com.powerload.entity.AlertEvent event) {
        try {
            pushService.pushAlert(event);
            runtimeStateService.markPushed(event.getId());
        } catch (Exception e) {
            log.warn("Alert persisted but WebSocket push failed: alertId={}", event.getId(), e);
        }
        try {
            alertJudgementService.judgeRuleBased(event);
        } catch (Exception e) {
            log.warn("Alert judgement failed: alertId={}", event.getId(), e);
        }
        eventPublisher.publishEvent(new AlertCreatedEvent(this, event));
    }
}
