package com.powerload.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.alert.AlertCreatedEvent;
import com.powerload.alert.AlertJudgementService;
import com.powerload.dto.response.GridRiskSnapshot;
import com.powerload.entity.AlertEvent;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.service.AlertEventService;
import com.powerload.service.GridTopologyService;
import com.powerload.websocket.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 拓扑风险告警调度。
 *
 * <p>只为去重后的根风险节点创建告警事件，子节点风险保留在拓扑快照中展示，
 * 不重复写入告警中心。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TopologyAlertScheduler {

    private static final String ALERT_TYPE = "TOPOLOGY_RISK";

    private final GridTopologyService gridTopologyService;
    private final AlertEventService alertEventService;
    private final AlertEventMapper alertEventMapper;
    private final PushService pushService;
    private final ApplicationEventPublisher eventPublisher;
    private final AlertJudgementService alertJudgementService;

    private final Map<Long, String> activeLevels = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 15_000)
    public void checkTopologyAlerts() {
        try {
            List<GridRiskSnapshot> roots = gridTopologyService.getAlertRoots();
            Set<Long> activeRootIds = new HashSet<>();
            for (GridRiskSnapshot snapshot : roots) {
                if (riskRank(snapshot.getRiskLevel()) < 2) {
                    continue;
                }
                activeRootIds.add(snapshot.getNodeId());
                String activeLevel = activeLevels.get(snapshot.getNodeId());
                if (activeLevel != null && riskRank(snapshot.getRiskLevel()) <= riskRank(activeLevel)) {
                    continue;
                }

                LocalDateTime triggerTime = LocalDateTime.now();
                if (alertEventService.isDuplicate(triggerTime, snapshot.getRiskLevel(),
                        null, snapshot.getNodeId(), ALERT_TYPE)) {
                    activeLevels.put(snapshot.getNodeId(), snapshot.getRiskLevel());
                    continue;
                }

                AlertEvent event = toAlertEvent(snapshot, triggerTime);
                alertEventService.save(event);
                event.setRootEventId(event.getId());
                alertEventMapper.updateById(event);
                pushService.pushAlert(event);
                try {
                    alertJudgementService.judgeRuleBased(event);
                } catch (Exception e) {
                    log.warn("拓扑告警规则研判失败: alertId={}, reason={}", event.getId(), e.getMessage());
                }
                eventPublisher.publishEvent(new AlertCreatedEvent(this, event));
                activeLevels.put(snapshot.getNodeId(), snapshot.getRiskLevel());
                log.info("拓扑根告警触发: node={}, level={}, impact={}MW",
                        snapshot.getNodeCode(), snapshot.getRiskLevel(), snapshot.getCurrentLoadMw());
            }

            activeLevels.keySet().removeIf(nodeId -> {
                if (activeRootIds.contains(nodeId)) {
                    return false;
                }
                alertEventService.resolveLatest(null, nodeId, ALERT_TYPE, LocalDateTime.now());
                return true;
            });
            resolveStalePersistedAlerts(activeRootIds);
        } catch (Exception e) {
            log.error("拓扑告警检测异常", e);
        }
    }

    private void resolveStalePersistedAlerts(Set<Long> activeRootIds) {
        List<AlertEvent> events = alertEventMapper.selectList(
                new LambdaQueryWrapper<AlertEvent>()
                        .eq(AlertEvent::getType, ALERT_TYPE)
                        .ne(AlertEvent::getStatus, "RECOVERED")
                        .isNotNull(AlertEvent::getNodeId));
        LocalDateTime resolvedAt = LocalDateTime.now();
        for (AlertEvent event : events) {
            if (activeRootIds.contains(event.getNodeId())) {
                continue;
            }
            AlertEvent update = new AlertEvent();
            update.setId(event.getId());
            update.setStatus("RECOVERED");
            update.setResolvedAt(resolvedAt);
            alertEventMapper.updateById(update);
            log.info("拓扑历史告警已恢复: alertId={}, nodeId={}", event.getId(), event.getNodeId());
        }
    }

    private AlertEvent toAlertEvent(GridRiskSnapshot snapshot, LocalDateTime triggerTime) {
        float current = snapshot.getCurrentLoadMw() != null ? snapshot.getCurrentLoadMw() : 0f;
        float threshold = snapshot.getRatedCapacityMw() != null ? snapshot.getRatedCapacityMw() : current;
        AlertEvent event = new AlertEvent();
        event.setNodeId(snapshot.getNodeId());
        event.setTriggerTime(triggerTime);
        event.setLevel(snapshot.getRiskLevel());
        event.setType(ALERT_TYPE);
        event.setCurrentValue(current);
        event.setThresholdValue(threshold);
        event.setImpactLoadMw(current);
        event.setAiAnalysis("拓扑根告警：" + snapshot.getNodeName()
                + " 风险等级 " + snapshot.getRiskLevel()
                + "，当前负荷 " + format(current)
                + " MW，预测峰值 " + format(snapshot.getForecastPeakMw())
                + " MW，容量余量 " + format(snapshot.getHeadroomMw())
                + " MW。");
        event.setSuggestion("建议优先核查该根节点及其下游馈线负荷分布，必要时评估转供或削峰方案。");
        event.setIsRead(0);
        event.setStatus("ACTIVE");
        event.setCreatedAt(LocalDateTime.now());
        return event;
    }

    private int riskRank(String riskLevel) {
        return switch (riskLevel) {
            case "RED" -> 4;
            case "ORANGE" -> 3;
            case "YELLOW" -> 2;
            default -> 0;
        };
    }

    private String format(Float value) {
        return value == null ? "--" : String.format("%.1f", value);
    }
}
