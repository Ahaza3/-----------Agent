package com.powerload.scheduler;

import com.powerload.alert.AlertCreatedEvent;
import com.powerload.alert.AlertJudgementService;
import com.powerload.alert.AlertTemplate;
import com.powerload.alert.ThresholdDetector;
import com.powerload.common.GridTopologyConstants;
import com.powerload.dto.response.AlertJudgementResult;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.AlertEvent;
import com.powerload.entity.AlertRule;
import com.powerload.security.SysUserPrincipal;
import com.powerload.service.AlertEventService;
import com.powerload.service.AlertRuleService;
import com.powerload.service.RealtimeLoadService;
import com.powerload.service.TicketService;
import com.powerload.websocket.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警检测调度 — 每秒检查最新实时负荷，匹配规则，触发告警
 *
 * <p>流程：
 * <ol>
 *   <li>取最新一条实时负荷（与 WebSocket 大屏同源）</li>
 *   <li>遍历所有启用的告警规则</li>
 *   <li>ThresholdDetector 判断是否超阈值</li>
 *   <li>去重检查</li>
 *   <li>AlertTemplate 生成文案</li>
 *   <li>存入 alert_event 表</li>
 *   <li>WebSocket 推送前端</li>
 * </ol>
 *
 * <p>从实时点产生告警到 WebSocket 推送，目标延迟 &lt; 5 秒。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertScheduler {

    private final RealtimeLoadService realtimeLoadService;
    private final AlertRuleService alertRuleService;
    private final AlertEventService alertEventService;
    private final ThresholdDetector thresholdDetector;
    private final AlertTemplate alertTemplate;
    private final PushService pushService;
    private final AlertJudgementService alertJudgementService;
    private final TicketService ticketService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /** 当前仍处于告警状态的最高级别，恢复安全后清除。 */
    private final Map<Long, String> activeLevels = new ConcurrentHashMap<>();
    /** 规则 + 级别最近一次触发时间，用于 coolingTime 去重。 */
    private final Map<String, Long> lastTriggeredAt = new ConcurrentHashMap<>();
    /** 规则首次连续超限时间，用于 triggerDuration。 */
    private final Map<Long, Long> overLimitSince = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 1_000) // 每秒检查
    public void checkAlerts() {
        try {
            RealtimeLoadPoint latest = realtimeLoadService.getLatest();
            if (latest == null) return;

            float currentLoad = latest.getLoadMw();
            List<AlertRule> rules = alertRuleService.listActive();

            for (AlertRule rule : rules) {
                String level = thresholdDetector.detect(currentLoad, rule.getConfig());
                String activeLevel = activeLevels.get(rule.getId());

                if (level == null) {
                    if (activeLevel != null && thresholdDetector.canRecover(currentLoad, rule.getConfig())) {
                        activeLevels.remove(rule.getId());
                        alertEventService.resolveLatest(rule.getId(), LocalDateTime.now());
                        log.info("告警恢复: ruleId={}, previousLevel={}, current={}MW",
                                rule.getId(), activeLevel, currentLoad);
                    }
                    overLimitSince.remove(rule.getId());
                    continue;
                }

                if (thresholdDetector.isSuppressed(rule.getConfig(), LocalDateTime.now())) {
                    overLimitSince.remove(rule.getId());
                    continue;
                }

                long now = System.currentTimeMillis();
                if (activeLevel == null) {
                    long startedAt = overLimitSince.computeIfAbsent(rule.getId(), ignored -> now);
                    int triggerDuration = thresholdDetector.getTriggerDuration(rule.getConfig());
                    if (now - startedAt < triggerDuration * 1_000L) continue;
                }

                // 下降过程不重复触发较低级别，直到负荷完全回到安全区。
                if (activeLevel != null && severity(level) <= severity(activeLevel)) continue;

                int coolingTime = Math.max(0, thresholdDetector.getCoolingTime(rule.getConfig()));
                String coolingKey = rule.getId() + ":" + level;
                long lastTriggered = lastTriggeredAt.getOrDefault(coolingKey, 0L);
                if (now - lastTriggered < coolingTime * 1_000L) continue;

                LocalDateTime triggerTime = LocalDateTime.now();
                if (alertEventService.isDuplicate(triggerTime, level, rule.getId())) {
                    activeLevels.put(rule.getId(), level);
                    lastTriggeredAt.put(coolingKey, now);
                    continue;
                }

                float threshold = thresholdDetector.getThreshold(rule.getConfig());
                AlertEvent event = new AlertEvent();
                event.setNodeId(GridTopologyConstants.ROOT_NODE_ID);
                event.setTriggerTime(triggerTime);
                event.setLevel(level);
                event.setType("THRESHOLD");
                event.setCurrentValue(currentLoad);
                event.setThresholdValue(threshold);
                event.setImpactLoadMw(currentLoad);
                event.setRuleId(rule.getId());
                event.setAiAnalysis(alertTemplate.generateAnalysis(level, currentLoad, threshold));
                event.setSuggestion(alertTemplate.generateSuggestion(level));
                event.setIsRead(0);
                event.setStatus("ACTIVE");
                event.setCreatedAt(LocalDateTime.now());

                alertEventService.save(event);
                pushService.pushAlert(event);

                // 智能研判
                try {
                    alertJudgementService.judgeRuleBased(event);
                } catch (Exception e) {
                    log.warn("智能研判或待确认工单草稿处理失败: alertId={}", event.getId(), e);
                }

                eventPublisher.publishEvent(new AlertCreatedEvent(this, event));
                activeLevels.put(rule.getId(), level);
                lastTriggeredAt.put(coolingKey, now);
                log.info("告警触发: level={}, current={}MW, threshold={}MW", level, currentLoad, threshold);
            }
        } catch (Exception e) {
            log.error("告警检测异常", e);
        }
    }

    private int severity(String level) {
        return switch (level) {
            case "RED" -> 3;
            case "ORANGE" -> 2;
            case "YELLOW" -> 1;
            default -> 0;
        };
    }
}
