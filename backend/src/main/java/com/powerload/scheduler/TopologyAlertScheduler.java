package com.powerload.scheduler;

import com.powerload.alert.AlertCreatedEvent;
import com.powerload.alert.AlertJudgementService;
import com.powerload.alert.AlertRuntimeResult;
import com.powerload.alert.AlertRuntimeStateService;
import com.powerload.dto.response.GridRiskSnapshot;
import com.powerload.service.GridTopologyService;
import com.powerload.websocket.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/** Topology risk is a simulated deterministic rule and shares the persistent alert lifecycle. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TopologyAlertScheduler {
    private final GridTopologyService gridTopologyService;
    private final AlertRuntimeStateService runtimeStateService;
    private final PushService pushService;
    private final ApplicationEventPublisher eventPublisher;
    private final AlertJudgementService alertJudgementService;

    @Scheduled(fixedRate = 15_000)
    public void checkTopologyAlerts() {
        Set<Long> activeRoots = new HashSet<>();
        try {
            for (GridRiskSnapshot snapshot : gridTopologyService.getAlertRoots()) {
                if (rank(snapshot.getRiskLevel()) < 2) continue;
                activeRoots.add(snapshot.getNodeId());
                AlertRuntimeResult result = runtimeStateService.evaluateTopology(snapshot);
                if (result.getCreatedEvent() != null) publish(result.getCreatedEvent());
            }
            // Roots that no longer appear are resolved from their persisted state; no historical push is emitted.
            for (GridRiskSnapshot snapshot : gridTopologyService.getRiskSnapshot()) {
                if (!activeRoots.contains(snapshot.getNodeId())) runtimeStateService.recoverTopology(snapshot.getNodeId());
            }
        } catch (Exception e) { log.error("ALERT_STATE_DEGRADED topology evaluation failed", e); }
    }

    private void publish(com.powerload.entity.AlertEvent event) {
        try { pushService.pushAlert(event); runtimeStateService.markPushed(event.getId()); } catch (Exception e) { log.warn("Persisted topology alert push failed: {}", event.getId(), e); }
        try { alertJudgementService.judgeRuleBased(event); } catch (Exception e) { log.warn("Topology judgement failed: {}", event.getId(), e); }
        eventPublisher.publishEvent(new AlertCreatedEvent(this, event));
    }

    private int rank(String level) { return "RED".equals(level) ? 4 : "ORANGE".equals(level) ? 3 : "YELLOW".equals(level) ? 2 : 0; }
}
