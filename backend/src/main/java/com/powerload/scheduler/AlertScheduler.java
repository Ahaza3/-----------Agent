package com.powerload.scheduler;

import com.powerload.alert.AlertTemplate;
import com.powerload.alert.ThresholdDetector;
import com.powerload.entity.AlertEvent;
import com.powerload.entity.AlertRule;
import com.powerload.entity.LoadData;
import com.powerload.service.AlertEventService;
import com.powerload.service.AlertRuleService;
import com.powerload.service.LoadDataService;
import com.powerload.websocket.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警检测调度 — 每 5 分钟扫描最新负荷，匹配规则，触发告警
 *
 * <p>流程：
 * <ol>
 *   <li>取最新一条负荷数据</li>
 *   <li>遍历所有启用的告警规则</li>
 *   <li>ThresholdDetector 判断是否超阈值</li>
 *   <li>去重检查（同一小时同级别不重复）</li>
 *   <li>AlertTemplate 生成文案</li>
 *   <li>存入 alert_event 表</li>
 *   <li>WebSocket 推送前端</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertScheduler {

    private final LoadDataService loadDataService;
    private final AlertRuleService alertRuleService;
    private final AlertEventService alertEventService;
    private final ThresholdDetector thresholdDetector;
    private final AlertTemplate alertTemplate;
    private final PushService pushService;

    @Scheduled(fixedRate = 300_000) // 5 分钟
    public void checkAlerts() {
        try {
            LoadData latest = loadDataService.getLatest();
            if (latest == null || latest.getLoadMw() == null) return;

            float currentLoad = latest.getLoadMw();
            List<AlertRule> rules = alertRuleService.listActive();

            for (AlertRule rule : rules) {
                String level = thresholdDetector.detect(currentLoad, rule.getConfig());
                if (level == null) continue;

                // 去重
                if (alertEventService.isDuplicate(latest.getTime(), level, rule.getId())) {
                    log.debug("告警已存在，跳过: level={}, ruleId={}", level, rule.getId());
                    continue;
                }

                // 生成事件
                float threshold = thresholdDetector.getThreshold(rule.getConfig());
                AlertEvent event = new AlertEvent();
                event.setTriggerTime(latest.getTime());
                event.setLevel(level);
                event.setType("THRESHOLD");
                event.setCurrentValue(currentLoad);
                event.setThresholdValue(threshold);
                event.setRuleId(rule.getId());
                event.setAiAnalysis(alertTemplate.generateAnalysis(level, currentLoad, threshold));
                event.setSuggestion(alertTemplate.generateSuggestion(level));
                event.setIsRead(0);
                event.setCreatedAt(LocalDateTime.now());

                alertEventService.save(event);
                pushService.pushAlert(event);
            }
        } catch (Exception e) {
            log.error("告警检测异常", e);
        }
    }
}
