package com.powerload.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.dto.response.GridRiskSnapshot;
import com.powerload.entity.AlertEvent;
import com.powerload.entity.AlertRule;
import com.powerload.entity.AlertRuntimeState;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.mapper.AlertRuntimeStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Database-backed single-instance alert lifecycle. AlertEvent remains immutable occurrence history. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuntimeStateService {
    private static final String ALERT_TYPE = "THRESHOLD";
    private final AlertRuntimeStateMapper runtimeMapper;
    private final AlertEventMapper eventMapper;
    private final ThresholdDetector thresholdDetector;
    private final AlertTemplate alertTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public AlertRuntimeResult evaluate(AlertRule rule, RealtimeLoadPoint point) {
        if (point == null || point.getObservedAt() == null || point.getReceivedAt() == null
                || point.getSourceInstanceId() == null || point.getSourceInstanceId().isBlank()
                || point.getQualityCode() == null || !"GOOD".equals(point.getQualityCode())) {
            return AlertRuntimeResult.unchanged();
        }
        Long nodeId = point.getNodeId() == null ? 1L : point.getNodeId();
        String stateKey = stateKey(nodeId, rule.getId(), ALERT_TYPE);
        AlertRuntimeState state = lockOrCreate(stateKey, nodeId, rule.getId(), ALERT_TYPE);
        if (sameSource(state, point)) return AlertRuntimeResult.unchanged();

        LocalDateTime now = point.getReceivedAt();
        state.setLastObservedAt(point.getObservedAt());
        state.setLastReceivedAt(point.getReceivedAt());
        state.setLastEvaluatedAt(now);
        state.setLastSourceInstanceId(point.getSourceInstanceId());
        state.setLastSourceSequence(point.getSequence());
        state.setRuleVersion(ruleVersion(rule));

        if (thresholdDetector.isSuppressed(rule.getConfig(), now)) {
            state.setLifecycleState("SUPPRESSED");
            state.setMaintenanceSuppressed(true);
            state.setSuppressedCount((state.getSuppressedCount() == null ? 0 : state.getSuppressedCount()) + 1);
            persist(state);
            return AlertRuntimeResult.unchanged();
        }
        state.setMaintenanceSuppressed(false);
        String level = thresholdDetector.detect(point.getLoadMw(), rule.getConfig());
        if (level == null || ("ACTIVE".equals(state.getLifecycleState())
                && thresholdDetector.canRecover(point.getLoadMw(), rule.getConfig()))) {
            return recoverOrNormalize(state, now);
        }
        if (level == null) {
            persist(state);
            return AlertRuntimeResult.unchanged();
        }

        if (!"ACTIVE".equals(state.getLifecycleState())) {
            if (state.getOverLimitSince() == null) {
                state.setOverLimitSince(point.getObservedAt());
                state.setLifecycleState("PENDING");
                persist(state);
                return AlertRuntimeResult.unchanged();
            }
            if (point.getObservedAt().isBefore(state.getOverLimitSince()
                    .plusSeconds(thresholdDetector.getTriggerDuration(rule.getConfig())))) {
                state.setLifecycleState("PENDING");
                persist(state);
                return AlertRuntimeResult.unchanged();
            }
            if (state.getCooldownUntil() != null && now.isBefore(state.getCooldownUntil())) {
                persist(state);
                return AlertRuntimeResult.unchanged();
            }
            return activate(state, rule, point, level, now);
        }

        if (severity(level) > severity(state.getCurrentLevel())) {
            AlertEvent current = eventMapper.selectById(state.getActiveAlertId());
            if (current != null) {
                current.setLevel(level);
                current.setEvidenceSnapshot(json(evidence(rule, point, level, state, "ESCALATED")));
                eventMapper.updateById(current);
            }
            state.setCurrentLevel(level);
        }
        persist(state);
        return AlertRuntimeResult.unchanged();
    }

    @Transactional
    public AlertRuntimeResult evaluateTopology(GridRiskSnapshot snapshot) {
        String stateKey = stateKey(snapshot.getNodeId(), 0L, "TOPOLOGY_RISK");
        AlertRuntimeState state = lockOrCreate(stateKey, snapshot.getNodeId(), 0L, "TOPOLOGY_RISK");
        LocalDateTime now = LocalDateTime.now();
        state.setLastEvaluatedAt(now);
        if (severity(snapshot.getRiskLevel()) < 1) return recoverTopology(state, now);
        if ("ACTIVE".equals(state.getLifecycleState()) && severity(snapshot.getRiskLevel()) <= severity(state.getCurrentLevel())) {
            persist(state);
            return AlertRuntimeResult.unchanged();
        }
        if ("ACTIVE".equals(state.getLifecycleState())) {
            AlertEvent event = eventMapper.selectById(state.getActiveAlertId());
            if (event != null) { event.setLevel(snapshot.getRiskLevel()); eventMapper.updateById(event); }
            state.setCurrentLevel(snapshot.getRiskLevel()); persist(state); return AlertRuntimeResult.unchanged();
        }
        int occurrence = (state.getOccurrenceNo() == null ? 0 : state.getOccurrenceNo()) + 1;
        AlertEvent previous = eventMapper.selectOne(new LambdaQueryWrapper<AlertEvent>().eq(AlertEvent::getStateKey, stateKey)
                .orderByDesc(AlertEvent::getOccurrenceNo).last("LIMIT 1"));
        AlertEvent event = new AlertEvent();
        event.setNodeId(snapshot.getNodeId()); event.setTriggerTime(now); event.setLevel(snapshot.getRiskLevel()); event.setType("TOPOLOGY_RISK");
        event.setCurrentValue(snapshot.getCurrentLoadMw()); event.setThresholdValue(snapshot.getRatedCapacityMw()); event.setImpactLoadMw(snapshot.getCurrentLoadMw());
        event.setAiAnalysis("拓扑模拟风险: " + snapshot.getNodeName() + "，" + snapshot.getRiskReason()); event.setSuggestion("建议核查节点负荷分布并评估转供方案。");
        event.setIsRead(0); event.setStatus("ACTIVE"); event.setOccurrenceNo(occurrence); event.setStateKey(stateKey);
        event.setEvidenceSnapshot(json(Map.of("nodeId", snapshot.getNodeId(), "nodeCode", snapshot.getNodeCode(), "riskBasis", snapshot.getRiskBasis() == null ? "UNKNOWN" : snapshot.getRiskBasis(), "simulated", true)));
        event.setTopologyVersion("demo-v1"); event.setTopologySimulated(true); event.setDetectedAt(now); event.setPersistedAt(now); event.setPreviousAlertId(previous == null ? null : previous.getId()); event.setCreatedAt(now);
        eventMapper.insert(event);
        state.setLifecycleState("ACTIVE"); state.setCurrentLevel(snapshot.getRiskLevel()); state.setActiveAlertId(event.getId()); state.setOccurrenceNo(occurrence); state.setLastTriggeredAt(now); persist(state);
        return new AlertRuntimeResult(event);
    }

    @Transactional
    public void recoverTopology(Long nodeId) {
        AlertRuntimeState state = runtimeMapper.selectByStateKeyForUpdate(stateKey(nodeId, 0L, "TOPOLOGY_RISK"));
        if (state != null) recoverTopology(state, LocalDateTime.now());
    }

    @Transactional
    public void markPushed(Long alertId) {
        AlertEvent update = new AlertEvent();
        update.setId(alertId);
        update.setPushedAt(LocalDateTime.now());
        eventMapper.updateById(update);
    }

    private AlertRuntimeResult activate(AlertRuntimeState state, AlertRule rule, RealtimeLoadPoint point,
                                        String level, LocalDateTime now) {
        int occurrence = (state.getOccurrenceNo() == null ? 0 : state.getOccurrenceNo()) + 1;
        AlertEvent previous = eventMapper.selectOne(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getStateKey, state.getStateKey()).orderByDesc(AlertEvent::getOccurrenceNo).last("LIMIT 1"));
        float threshold = thresholdDetector.getThreshold(rule.getConfig());
        AlertEvent event = new AlertEvent();
        event.setNodeId(state.getNodeId());
        event.setTriggerTime(point.getObservedAt());
        event.setLevel(level);
        event.setType(ALERT_TYPE);
        event.setCurrentValue(point.getLoadMw());
        event.setThresholdValue(threshold);
        event.setImpactLoadMw(point.getLoadMw());
        event.setRuleId(rule.getId());
        event.setAiAnalysis(alertTemplate.generateAnalysis(level, point.getLoadMw(), threshold));
        event.setSuggestion(alertTemplate.generateSuggestion(level));
        event.setIsRead(0);
        event.setStatus("ACTIVE");
        event.setOccurrenceNo(occurrence);
        event.setStateKey(state.getStateKey());
        event.setRuleVersion(state.getRuleVersion());
        event.setRuleSnapshot(json(ruleSnapshot(rule, state)));
        event.setEvidenceSnapshot(json(evidence(rule, point, level, state, "THRESHOLD_EXCEEDED")));
        event.setDataSource(point.getDataSource());
        event.setSourceInstanceId(point.getSourceInstanceId());
        event.setSourceSequence(point.getSequence());
        event.setSourceObservedAt(point.getObservedAt());
        event.setSourceReceivedAt(point.getReceivedAt());
        event.setDetectedAt(now);
        event.setPersistedAt(now);
        event.setPreviousAlertId(previous == null ? null : previous.getId());
        event.setCreatedAt(now);
        eventMapper.insert(event);

        state.setLifecycleState("ACTIVE");
        state.setCurrentLevel(level);
        state.setActiveAlertId(event.getId());
        state.setOccurrenceNo(occurrence);
        state.setLastTriggeredAt(now);
        state.setCooldownUntil(now.plusSeconds(Math.max(0, thresholdDetector.getCoolingTime(rule.getConfig()))));
        persist(state);
        return new AlertRuntimeResult(event);
    }

    private AlertRuntimeResult recoverOrNormalize(AlertRuntimeState state, LocalDateTime now) {
        if (!"ACTIVE".equals(state.getLifecycleState())) {
            state.setLifecycleState("NORMAL");
            state.setOverLimitSince(null);
            persist(state);
            return AlertRuntimeResult.unchanged();
        }
        AlertEvent event = eventMapper.selectById(state.getActiveAlertId());
        if (event != null) {
            event.setResolvedAt(now); // legacy-compatible lifecycle display
            event.setRecoveredAt(now);
            if (!"ACKNOWLEDGED".equals(event.getStatus())) event.setStatus("RECOVERED");
            eventMapper.updateById(event);
        }
        state.setLifecycleState("RECOVERED");
        state.setRecoveredAt(now);
        state.setActiveAlertId(null);
        state.setCurrentLevel(null);
        state.setOverLimitSince(null);
        persist(state);
        return AlertRuntimeResult.unchanged();
    }

    private AlertRuntimeResult recoverTopology(AlertRuntimeState state, LocalDateTime now) {
        if (!"ACTIVE".equals(state.getLifecycleState())) { state.setLifecycleState("NORMAL"); persist(state); return AlertRuntimeResult.unchanged(); }
        AlertEvent event = eventMapper.selectById(state.getActiveAlertId());
        if (event != null) { event.setResolvedAt(now); event.setRecoveredAt(now); if (!"ACKNOWLEDGED".equals(event.getStatus())) event.setStatus("RECOVERED"); eventMapper.updateById(event); }
        state.setLifecycleState("RECOVERED"); state.setRecoveredAt(now); state.setActiveAlertId(null); state.setCurrentLevel(null); persist(state);
        return AlertRuntimeResult.unchanged();
    }

    private AlertRuntimeState lockOrCreate(String stateKey, Long nodeId, Long ruleId, String type) {
        for (int attempt = 0; attempt < 2; attempt++) {
            AlertRuntimeState state = runtimeMapper.selectByStateKeyForUpdate(stateKey);
            if (state != null) return state;
            AlertRuntimeState created = new AlertRuntimeState();
            created.setStateKey(stateKey); created.setNodeId(nodeId); created.setRuleId(ruleId);
            created.setAlertType(type); created.setLifecycleState("NORMAL"); created.setOccurrenceNo(0);
            created.setMaintenanceSuppressed(false); created.setSuppressedCount(0); created.setOptimisticVersion(0);
            try { runtimeMapper.insert(created); } catch (DuplicateKeyException ignored) { }
        }
        AlertRuntimeState state = runtimeMapper.selectByStateKeyForUpdate(stateKey);
        if (state == null) throw new IllegalStateException("cannot lock alert runtime state: " + stateKey);
        return state;
    }

    private void persist(AlertRuntimeState state) {
        state.setOptimisticVersion((state.getOptimisticVersion() == null ? 0 : state.getOptimisticVersion()) + 1);
        runtimeMapper.updateById(state);
    }

    private boolean sameSource(AlertRuntimeState state, RealtimeLoadPoint point) {
        return point.getSourceInstanceId().equals(state.getLastSourceInstanceId())
                && state.getLastSourceSequence() != null
                && point.getSequence() <= state.getLastSourceSequence();
    }

    private String stateKey(Long nodeId, Long ruleId, String type) { return nodeId + ":" + ruleId + ":" + type; }
    private String ruleVersion(AlertRule rule) { return rule.getId() + "@" + (rule.getUpdatedAt() == null ? "unknown" : rule.getUpdatedAt()); }
    private int severity(String level) { return "RED".equals(level) ? 3 : "ORANGE".equals(level) ? 2 : "YELLOW".equals(level) ? 1 : 0; }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("snapshot serialization failed", e); } }
    private Map<String, Object> ruleSnapshot(AlertRule rule, AlertRuntimeState state) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("ruleId", rule.getId()); result.put("ruleVersion", state.getRuleVersion());
        result.put("type", rule.getType()); result.put("config", rule.getConfig()); result.put("maintenanceSuppressed", state.getMaintenanceSuppressed()); return result;
    }
    private Map<String, Object> evidence(AlertRule rule, RealtimeLoadPoint point, String level, AlertRuntimeState state, String reason) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("nodeId", state.getNodeId()); result.put("currentLoadMw", point.getLoadMw());
        result.put("matchedThresholdMw", thresholdDetector.getThreshold(rule.getConfig())); result.put("calculatedLevel", level);
        result.put("observedAt", point.getObservedAt()); result.put("receivedAt", point.getReceivedAt()); result.put("sequence", point.getSequence());
        result.put("qualityCode", point.getQualityCode()); result.put("dataSource", point.getDataSource()); result.put("estimated", point.isEstimated());
        result.put("continuousOverLimitSince", state.getOverLimitSince()); result.put("simulated", true); result.put("reason", reason); return result;
    }
}
