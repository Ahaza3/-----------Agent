package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerload.entity.AlertEvent;
import com.powerload.entity.AlertDeliveryMetric;
import com.powerload.dto.request.AlertDeliveryAckRequest;
import com.powerload.dto.response.AlertDeliveryMetricsResponse;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.mapper.AlertDeliveryMetricMapper;
import com.powerload.security.SysUserPrincipal;
import com.powerload.service.AlertEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEventServiceImpl implements AlertEventService {

    private final AlertEventMapper alertEventMapper;
    private final AlertDeliveryMetricMapper alertDeliveryMetricMapper;

    @Override
    public Page<AlertEvent> query(int page, int size, String level,
                                  LocalDateTime start, LocalDateTime end, boolean unreadOnly) {
        return query(page, size, level, null, null, null, start, end, unreadOnly);
    }

    @Override
    public Page<AlertEvent> query(int page, int size, String level, String type,
                                  String status, String keyword,
                                  LocalDateTime start, LocalDateTime end,
                                  boolean unreadOnly) {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();

        if (level != null && !level.isEmpty()) {
            wrapper.eq(AlertEvent::getLevel, level);
        }
        if (type != null && !type.isEmpty()) {
            wrapper.eq(AlertEvent::getType, type);
        }
        if (status != null && !status.isEmpty()) {
            if ("RECOVERED".equals(status)) {
                wrapper.isNotNull(AlertEvent::getRecoveredAt);
            } else {
                wrapper.eq(AlertEvent::getStatus, status);
            }
        }
        if (keyword != null && !keyword.isBlank()) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(query -> query
                    .like(AlertEvent::getAiAnalysis, normalizedKeyword)
                    .or()
                    .like(AlertEvent::getSuggestion, normalizedKeyword)
                    .or()
                    .like(AlertEvent::getType, normalizedKeyword));
        }
        if (start != null) {
            wrapper.ge(AlertEvent::getTriggerTime, start);
        }
        if (end != null) {
            wrapper.le(AlertEvent::getTriggerTime, end);
        }
        if (unreadOnly) {
            wrapper.eq(AlertEvent::getIsRead, 0);
        }
        wrapper.orderByDesc(AlertEvent::getTriggerTime);

        return alertEventMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public void markRead(Long id) {
        AlertEvent event = new AlertEvent();
        event.setId(id);
        event.setIsRead(1);
        alertEventMapper.updateById(event);
        log.debug("告警已读: id={}", id);
    }

    @Override
    public AlertEvent save(AlertEvent event) {
        if (event.getStatus() == null || event.getStatus().isBlank()) {
            event.setStatus("ACTIVE");
        }
        alertEventMapper.insert(event);
        return event;
    }

    @Override
    public boolean isDuplicate(LocalDateTime triggerTime, String level, Long ruleId) {
        // 同一小时内的同级别 + 同规则视为重复
        LocalDateTime hourStart = triggerTime.withMinute(0).withSecond(0).withNano(0);
        LocalDateTime hourEnd = hourStart.plusHours(1);

        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertEvent::getLevel, level)
               .eq(AlertEvent::getRuleId, ruleId)
               .ge(AlertEvent::getTriggerTime, hourStart)
               .lt(AlertEvent::getTriggerTime, hourEnd);

        return alertEventMapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean isDuplicate(LocalDateTime triggerTime, String level, Long ruleId, Long nodeId, String type) {
        LocalDateTime hourStart = triggerTime.withMinute(0).withSecond(0).withNano(0);
        LocalDateTime hourEnd = hourStart.plusHours(1);

        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertEvent::getLevel, level)
               .eq(ruleId != null, AlertEvent::getRuleId, ruleId)
               .isNull(ruleId == null, AlertEvent::getRuleId)
               .eq(nodeId != null, AlertEvent::getNodeId, nodeId)
               .eq(type != null && !type.isBlank(), AlertEvent::getType, type)
               .ge(AlertEvent::getTriggerTime, hourStart)
               .lt(AlertEvent::getTriggerTime, hourEnd);

        return alertEventMapper.selectCount(wrapper) > 0;
    }

    @Override
    @Transactional
    public void acknowledge(Long id, SysUserPrincipal user) {
        AlertEvent event = alertEventMapper.selectById(id);
        if (event == null) {
            throw new IllegalArgumentException("告警不存在: " + id);
        }
        if ("RECOVERED".equals(event.getStatus())) {
            throw new IllegalStateException("已恢复的告警不可确认");
        }

        AlertEvent update = new AlertEvent();
        update.setId(id);
        update.setStatus("ACKNOWLEDGED");
        update.setAcknowledgedAt(LocalDateTime.now());
        update.setAcknowledgedBy(user != null ? user.getUserId() : null);
        update.setAcknowledgedByName(user != null ? user.getUsername() : null);
        alertEventMapper.updateById(update);
    }

    @Override
    @Transactional
    public void resolveLatest(Long ruleId, LocalDateTime resolvedAt) {
        resolveLatest(ruleId, null, null, resolvedAt);
    }

    @Override
    @Transactional
    public void resolveLatest(Long ruleId, Long nodeId, String type, LocalDateTime resolvedAt) {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<AlertEvent>()
                .eq(ruleId != null, AlertEvent::getRuleId, ruleId)
                .isNull(ruleId == null, AlertEvent::getRuleId)
                .eq(nodeId != null, AlertEvent::getNodeId, nodeId)
                .eq(type != null && !type.isBlank(), AlertEvent::getType, type)
                .ne(AlertEvent::getStatus, "RECOVERED")
                .orderByDesc(AlertEvent::getTriggerTime)
                .last("LIMIT 1");
        AlertEvent latest = alertEventMapper.selectOne(wrapper);
        if (latest == null) {
            return;
        }

        AlertEvent update = new AlertEvent();
        update.setId(latest.getId());
        update.setStatus("RECOVERED");
        update.setResolvedAt(resolvedAt);
        alertEventMapper.updateById(update);
    }

    @Override
    public Map<String, Object> metrics(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<AlertEvent>()
                .ge(start != null, AlertEvent::getTriggerTime, start)
                .lt(end != null, AlertEvent::getTriggerTime, end)
                .orderByAsc(AlertEvent::getTriggerTime);
        List<AlertEvent> events = alertEventMapper.selectList(wrapper);

        long acknowledged = events.stream()
                .filter(event -> event.getAcknowledgedAt() != null)
                .count();
        long recovered = events.stream()
                .filter(event -> event.getResolvedAt() != null)
                .count();
        double ackMinutes = averageMinutes(events, true);
        double recoveryMinutes = averageMinutes(events, false);

        Map<String, LocalDateTime> lastByKey = new HashMap<>();
        long duplicates = 0;
        for (AlertEvent event : events) {
            String key = event.getRuleId() + ":" + event.getLevel();
            LocalDateTime previous = lastByKey.put(key, event.getTriggerTime());
            if (previous != null && event.getTriggerTime() != null
                    && Duration.between(previous, event.getTriggerTime()).toMinutes() <= 60) {
                duplicates++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("start", start);
        result.put("end", end);
        result.put("total", events.size());
        result.put("red", countLevel(events, "RED"));
        result.put("orange", countLevel(events, "ORANGE"));
        result.put("yellow", countLevel(events, "YELLOW"));
        result.put("unread", events.stream().filter(event -> Integer.valueOf(0).equals(event.getIsRead())).count());
        result.put("acknowledged", acknowledged);
        result.put("recovered", recovered);
        result.put("active", countStatus(events, "ACTIVE"));
        result.put("acknowledgedStatus", countStatus(events, "ACKNOWLEDGED"));
        result.put("topologyRisk", countType(events, "TOPOLOGY_RISK"));
        result.put("threshold", countType(events, "THRESHOLD"));
        result.put("trend", countType(events, "TREND"));
        result.put("anomaly", countType(events, "ANOMALY"));
        result.put("duplicateCount", duplicates);
        result.put("duplicateRate", events.isEmpty() ? 0d : duplicates * 100d / events.size());
        result.put("averageAckMinutes", ackMinutes);
        result.put("averageRecoveryMinutes", recoveryMinutes);
        return result;
    }

    @Override
    @Transactional
    public AlertDeliveryMetric acknowledgeDelivery(Long alertId, SysUserPrincipal user, AlertDeliveryAckRequest request) {
        if (user == null || user.getUserId() == null) throw new IllegalStateException("authentication required");
        AlertEvent alert = alertEventMapper.selectById(alertId);
        if (alert == null) throw new IllegalArgumentException("告警不存在: " + alertId);
        AlertDeliveryMetric existing = alertDeliveryMetricMapper.selectOne(new LambdaQueryWrapper<AlertDeliveryMetric>()
                .eq(AlertDeliveryMetric::getAlertId, alertId).eq(AlertDeliveryMetric::getUserId, user.getUserId()));
        if (existing != null) return existing;

        LocalDateTime receivedAt = LocalDateTime.now();
        AlertDeliveryMetric metric = new AlertDeliveryMetric();
        metric.setAlertId(alertId); metric.setUserId(user.getUserId()); metric.setUsername(user.getUsername()); metric.setRole(user.getRole());
        metric.setClientSessionId(request == null ? null : request.getClientSessionId());
        metric.setClientRenderedAt(request == null ? null : request.getClientRenderedAt());
        metric.setAckReceivedAt(receivedAt); metric.setCreatedAt(receivedAt);
        if (alert.getSourceObservedAt() == null) {
            metric.setInvalidReason("LEGACY_EVIDENCE_MISSING");
        } else {
            long latency = Duration.between(alert.getSourceObservedAt(), receivedAt).toMillis();
            if (latency < 0) metric.setInvalidReason("CLOCK_SKEW"); else metric.setLatencyMs(latency);
        }
        try {
            alertDeliveryMetricMapper.insert(metric);
            return metric;
        } catch (DuplicateKeyException e) {
            return alertDeliveryMetricMapper.selectOne(new LambdaQueryWrapper<AlertDeliveryMetric>()
                    .eq(AlertDeliveryMetric::getAlertId, alertId).eq(AlertDeliveryMetric::getUserId, user.getUserId()));
        }
    }

    @Override
    public AlertDeliveryMetricsResponse deliveryMetrics(LocalDateTime start, LocalDateTime end, Long nodeId) {
        List<AlertDeliveryMetric> rows = alertDeliveryMetricMapper.selectList(new LambdaQueryWrapper<AlertDeliveryMetric>()
                .ge(start != null, AlertDeliveryMetric::getAckReceivedAt, start)
                .lt(end != null, AlertDeliveryMetric::getAckReceivedAt, end)
                .orderByAsc(AlertDeliveryMetric::getLatencyMs));
        List<Long> valid = new ArrayList<>();
        long legacy = 0, invalid = 0;
        for (AlertDeliveryMetric row : rows) {
            AlertEvent event = alertEventMapper.selectById(row.getAlertId());
            if (event == null || (nodeId != null && !nodeId.equals(event.getNodeId()))) continue;
            if (row.getLatencyMs() != null && row.getLatencyMs() >= 0) valid.add(row.getLatencyMs());
            else if ("LEGACY_EVIDENCE_MISSING".equals(row.getInvalidReason())) legacy++;
            else invalid++;
        }
        valid.sort(Long::compareTo);
        AlertDeliveryMetricsResponse result = new AlertDeliveryMetricsResponse();
        result.setStartTime(start); result.setEndTime(end); result.setNodeId(nodeId); result.setDeliverySamples(valid.size());
        result.setExcludedLegacyCount(legacy); result.setExcludedInvalidCount(invalid);
        if (!valid.isEmpty()) {
            result.setP50LatencyMs(nearestRank(valid, .50)); result.setP95LatencyMs(nearestRank(valid, .95));
            result.setMaxLatencyMs(valid.get(valid.size() - 1));
        }
        return result;
    }

    private long nearestRank(List<Long> sorted, double percentile) {
        return sorted.get((int) Math.ceil(percentile * sorted.size()) - 1);
    }

    private long countLevel(List<AlertEvent> events, String level) {
        return events.stream().filter(event -> level.equals(event.getLevel())).count();
    }

    private long countStatus(List<AlertEvent> events, String status) {
        return events.stream().filter(event -> status.equals(event.getStatus())).count();
    }

    private long countType(List<AlertEvent> events, String type) {
        return events.stream().filter(event -> type.equals(event.getType())).count();
    }

    private double averageMinutes(List<AlertEvent> events, boolean acknowledgement) {
        List<Long> values = new ArrayList<>();
        for (AlertEvent event : events) {
            LocalDateTime end = acknowledgement ? event.getAcknowledgedAt() : event.getResolvedAt();
            if (event.getTriggerTime() != null && end != null) {
                values.add(Duration.between(event.getTriggerTime(), end).toSeconds());
            }
        }
        return values.stream().mapToLong(Long::longValue).average().orElse(0d) / 60d;
    }
}
