package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.powerload.common.GridTopologyConstants;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.dto.response.RealtimeLoadStatus;
import com.powerload.entity.RealtimeTelemetry;
import com.powerload.mapper.RealtimeTelemetryMapper;
import com.powerload.service.RealtimeLoadService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Service
public class RealtimeLoadServiceImpl implements RealtimeLoadService {

    private static final int MAX_CAPACITY = 3600;
    private static final int RECOVERY_MINUTES = 30;
    private static final long STALE_SECONDS = 15;
    private static final long LATE_SECONDS = 15;
    private static final float STEP_MAX = 3.0f;
    private static final float REVERSION_RATE = 0.005f;
    private static final float NOISE_STDDEV = 1.0f;
    private static final float DEMO_STEP_MAX = 12.0f;
    private static final float SAFETY_THRESHOLD = 1100f;
    private static final float DEMO_PEAK_LOAD = 1240f;

    private final List<RealtimeLoadPoint> buffer = new ArrayList<>(MAX_CAPACITY);
    private final Set<String> acceptedKeys = new HashSet<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong sequence = new AtomicLong(0);
    private final String sourceInstanceId = UUID.randomUUID().toString();
    private final Random random;

    @Autowired(required = false)
    private RealtimeTelemetryMapper realtimeTelemetryMapper;

    private float currentLoad;
    private float currentTemp;
    private float currentHum;
    private float targetLoad;
    private boolean initialized;
    private DemoMode demoMode = DemoMode.NORMAL;
    private RealtimeLoadPoint latestAlertEligible;
    private RealtimeLoadPoint latestValidPoint;

    private enum DemoMode { NORMAL, SPIKE, RECOVERY }

    public RealtimeLoadServiceImpl() {
        this(new Random());
    }

    public RealtimeLoadServiceImpl(Random random) {
        this.random = random;
    }

    @PostConstruct
    public void recoverRecentTelemetry() {
        if (realtimeTelemetryMapper == null) {
            return;
        }
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(RECOVERY_MINUTES);
            List<RealtimeTelemetry> recovered = realtimeTelemetryMapper.selectList(
                    Wrappers.<RealtimeTelemetry>lambdaQuery()
                            .ge(RealtimeTelemetry::getReceivedAt, cutoff)
                            .orderByAsc(RealtimeTelemetry::getObservedAt)
                            .orderByAsc(RealtimeTelemetry::getSequence));
            lock.writeLock().lock();
            try {
                for (RealtimeTelemetry telemetry : recovered) {
                    RealtimeLoadPoint point = fromTelemetry(telemetry);
                    acceptedKeys.add(dedupKey(point));
                    if (!"BAD".equals(point.getQualityCode())) {
                        insertInObservedOrder(point);
                        latestValidPoint = latestByReceived(latestValidPoint, point);
                    }
                }
                trimBuffer();
            } finally {
                lock.writeLock().unlock();
            }
            log.info("Recovered {} realtime telemetry points into memory", buffer.size());
        } catch (Exception e) {
            log.warn("Realtime telemetry recovery unavailable: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void initialize(float initialLoad, float initialTemp, float initialHum) {
        lock.writeLock().lock();
        try {
            currentLoad = initialLoad;
            currentTemp = initialTemp;
            currentHum = initialHum;
            targetLoad = initialLoad;
            demoMode = DemoMode.NORMAL;
            initialized = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public RealtimeLoadPoint generateAndAppend() {
        RealtimeLoadPoint point;
        lock.writeLock().lock();
        try {
            ensureInitialized();
            float newLoad = nextLoad();
            float newTemp = nextTemperature();
            float newHum = nextHumidity();
            LocalDateTime now = LocalDateTime.now();
            point = new RealtimeLoadPoint();
            point.setTimestamp(toEpochMillis(now));
            point.setSequence(sequence.incrementAndGet());
            point.setLoadMw(newLoad);
            point.setTemperature(newTemp);
            point.setHumidity(newHum);
            point.setSource(demoMode == DemoMode.NORMAL ? "MOCK" : "MOCK_DEMO");
            point.setNodeId(GridTopologyConstants.ROOT_NODE_ID);
            point.setObservedAt(now);
            point.setReceivedAt(now);
            point.setSourceInstanceId(sourceInstanceId);
            point.setDataSource("MOCK_REALTIME");
            point.setEstimated(false);
            currentLoad = newLoad;
            currentTemp = newTemp;
            currentHum = newHum;
        } finally {
            lock.writeLock().unlock();
        }
        return accept(point);
    }

    @Override
    public RealtimeLoadPoint accept(RealtimeLoadPoint point) {
        if (point == null) {
            return null;
        }
        lock.writeLock().lock();
        try {
            normalize(point);
            determineQuality(point);
            String key = dedupKey(point);
            if (acceptedKeys.contains(key)) {
                return null;
            }
            PersistenceResult result = persist(point);
            if (result == PersistenceResult.DUPLICATE) {
                acceptedKeys.add(key);
                return null;
            }
            acceptedKeys.add(key);
            if (!"BAD".equals(point.getQualityCode())) {
                insertInObservedOrder(point);
                trimBuffer();
                latestValidPoint = latestByReceived(latestValidPoint, point);
                if ("GOOD".equals(point.getQualityCode())) {
                    latestAlertEligible = latestByObservation(latestAlertEligible, point);
                }
            }
            return withFreshness(copy(point));
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void normalize(RealtimeLoadPoint point) {
        LocalDateTime now = LocalDateTime.now();
        if (point.getNodeId() == null) point.setNodeId(GridTopologyConstants.ROOT_NODE_ID);
        if (point.getObservedAt() == null) point.setObservedAt(fromEpochMillis(point.getTimestamp(), now));
        if (point.getReceivedAt() == null) point.setReceivedAt(now);
        if (point.getReceivedAt().isBefore(point.getObservedAt())) point.setReceivedAt(point.getObservedAt());
        if (point.getSourceInstanceId() == null || point.getSourceInstanceId().isBlank()) {
            point.setSourceInstanceId(sourceInstanceId);
        }
        if (point.getSequence() <= 0) point.setSequence(sequence.incrementAndGet());
        if (sourceInstanceId.equals(point.getSourceInstanceId())) {
            sequence.accumulateAndGet(point.getSequence(), Math::max);
        }
        if (point.getDataSource() == null || point.getDataSource().isBlank()) point.setDataSource("MOCK_REALTIME");
        if (point.getSource() == null || point.getSource().isBlank()) point.setSource("MOCK");
        point.setTimestamp(toEpochMillis(point.getObservedAt()));
        if (point.getPersistenceStatus() == null) point.setPersistenceStatus("PERSISTED");
    }

    private void determineQuality(RealtimeLoadPoint point) {
        Float load = point.getLoadMw();
        if (load == null || !Float.isFinite(load) || load < 0) {
            point.setQualityCode("BAD");
            point.setQualityReason("INVALID_LOAD");
        } else if (Duration.between(point.getObservedAt(), point.getReceivedAt()).getSeconds() > LATE_SECONDS) {
            point.setQualityCode("LATE");
            point.setQualityReason("RECEIVED_LATE");
        } else if (point.isEstimated()) {
            point.setQualityCode("ESTIMATED");
            point.setQualityReason("ESTIMATED_VALUE");
        } else {
            point.setQualityCode("GOOD");
            point.setQualityReason(null);
        }
        point.setFreshnessStatus(freshnessStatus());
    }

    private PersistenceResult persist(RealtimeLoadPoint point) {
        if (realtimeTelemetryMapper == null) {
            point.setPersistenceStatus("PERSISTENCE_DEGRADED");
            return PersistenceResult.DEGRADED;
        }
        try {
            realtimeTelemetryMapper.insert(toTelemetry(point));
            point.setPersistenceStatus("PERSISTED");
            return PersistenceResult.PERSISTED;
        } catch (DuplicateKeyException e) {
            return PersistenceResult.DUPLICATE;
        } catch (Exception e) {
            point.setPersistenceStatus("PERSISTENCE_DEGRADED");
            log.warn("Realtime telemetry persistence degraded: {}", e.getClass().getSimpleName());
            return PersistenceResult.DEGRADED;
        }
    }

    private enum PersistenceResult { PERSISTED, DEGRADED, DUPLICATE }

    private RealtimeTelemetry toTelemetry(RealtimeLoadPoint point) {
        RealtimeTelemetry telemetry = new RealtimeTelemetry();
        telemetry.setNodeId(point.getNodeId());
        telemetry.setObservedAt(point.getObservedAt());
        telemetry.setReceivedAt(point.getReceivedAt());
        telemetry.setSourceInstanceId(point.getSourceInstanceId());
        telemetry.setSequence(point.getSequence());
        telemetry.setLoadMw(decimal(point.getLoadMw()));
        telemetry.setTemperature(decimal(point.getTemperature()));
        telemetry.setHumidity(decimal(point.getHumidity()));
        telemetry.setQualityCode(point.getQualityCode());
        telemetry.setDataSource(point.getDataSource());
        telemetry.setEstimated(point.isEstimated());
        telemetry.setPersistenceStatus("PERSISTED");
        telemetry.setQualityReason(point.getQualityReason());
        return telemetry;
    }

    private RealtimeLoadPoint fromTelemetry(RealtimeTelemetry telemetry) {
        RealtimeLoadPoint point = new RealtimeLoadPoint();
        point.setNodeId(telemetry.getNodeId());
        point.setObservedAt(telemetry.getObservedAt());
        point.setReceivedAt(telemetry.getReceivedAt());
        point.setTimestamp(toEpochMillis(telemetry.getObservedAt()));
        point.setSourceInstanceId(telemetry.getSourceInstanceId());
        point.setSequence(telemetry.getSequence());
        point.setLoadMw(telemetry.getLoadMw() == null ? null : telemetry.getLoadMw().floatValue());
        point.setTemperature(telemetry.getTemperature() == null ? null : telemetry.getTemperature().floatValue());
        point.setHumidity(telemetry.getHumidity() == null ? null : telemetry.getHumidity().floatValue());
        point.setQualityCode(telemetry.getQualityCode());
        point.setQualityReason(telemetry.getQualityReason());
        point.setDataSource(telemetry.getDataSource());
        point.setEstimated(Boolean.TRUE.equals(telemetry.getEstimated()));
        point.setPersistenceStatus(telemetry.getPersistenceStatus());
        point.setSource("MOCK_REALTIME".equals(telemetry.getDataSource()) ? "MOCK" : telemetry.getDataSource());
        return point;
    }

    private BigDecimal decimal(Float value) {
        return value == null || !Float.isFinite(value) ? null : BigDecimal.valueOf(value);
    }

    private void insertInObservedOrder(RealtimeLoadPoint point) {
        int index = 0;
        while (index < buffer.size() && compareObservation(buffer.get(index), point) <= 0) index++;
        buffer.add(index, copy(point));
    }

    private int compareObservation(RealtimeLoadPoint left, RealtimeLoadPoint right) {
        int time = left.getObservedAt().compareTo(right.getObservedAt());
        return time != 0 ? time : Long.compare(left.getSequence(), right.getSequence());
    }

    private void trimBuffer() {
        while (buffer.size() > MAX_CAPACITY) buffer.remove(0);
    }

    private RealtimeLoadPoint latestByObservation(RealtimeLoadPoint first, RealtimeLoadPoint second) {
        return first == null || compareObservation(first, second) < 0 ? copy(second) : first;
    }

    private RealtimeLoadPoint latestByReceived(RealtimeLoadPoint first, RealtimeLoadPoint second) {
        return first == null || first.getReceivedAt().isBefore(second.getReceivedAt()) ? copy(second) : first;
    }

    @Override
    public void setTargetLoad(float targetLoad) {
        lock.writeLock().lock();
        try {
            this.targetLoad = targetLoad;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public RealtimeLoadStatus enterNormalMode() {
        lock.writeLock().lock();
        try {
            ensureInitialized();
            currentLoad = targetLoad;
            demoMode = DemoMode.NORMAL;
            return buildStatus();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public RealtimeLoadStatus startSpikeDemo() {
        lock.writeLock().lock();
        try {
            ensureInitialized();
            demoMode = DemoMode.SPIKE;
            return buildStatus();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public RealtimeLoadStatus startRecoveryDemo() {
        lock.writeLock().lock();
        try {
            ensureInitialized();
            demoMode = DemoMode.RECOVERY;
            return buildStatus();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public RealtimeLoadStatus getStatus() {
        lock.readLock().lock();
        try {
            return buildStatus();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public RealtimeLoadPoint getLatest() {
        lock.readLock().lock();
        try {
            return buffer.isEmpty() ? null : withFreshness(copy(buffer.get(buffer.size() - 1)));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public RealtimeLoadPoint getLatestForAlert() {
        lock.readLock().lock();
        try {
            return latestAlertEligible == null ? null : withFreshness(copy(latestAlertEligible));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<RealtimeLoadPoint> getRecent(int minutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(0, minutes));
        lock.readLock().lock();
        try {
            List<RealtimeLoadPoint> result = new ArrayList<>();
            for (RealtimeLoadPoint point : buffer) {
                if (!point.getObservedAt().isBefore(cutoff)) result.add(withFreshness(copy(point)));
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return buffer.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    private String freshnessStatus() {
        if (latestValidPoint == null || latestValidPoint.getReceivedAt() == null) return "STALE";
        return Duration.between(latestValidPoint.getReceivedAt(), LocalDateTime.now()).getSeconds() > STALE_SECONDS
                ? "STALE" : "FRESH";
    }

    private RealtimeLoadPoint withFreshness(RealtimeLoadPoint point) {
        point.setFreshnessStatus(freshnessStatus());
        return point;
    }

    private RealtimeLoadPoint copy(RealtimeLoadPoint source) {
        RealtimeLoadPoint target = new RealtimeLoadPoint();
        target.setTimestamp(source.getTimestamp());
        target.setSequence(source.getSequence());
        target.setLoadMw(source.getLoadMw());
        target.setTemperature(source.getTemperature());
        target.setHumidity(source.getHumidity());
        target.setSource(source.getSource());
        target.setNodeId(source.getNodeId());
        target.setObservedAt(source.getObservedAt());
        target.setReceivedAt(source.getReceivedAt());
        target.setSourceInstanceId(source.getSourceInstanceId());
        target.setQualityCode(source.getQualityCode());
        target.setQualityReason(source.getQualityReason());
        target.setDataSource(source.getDataSource());
        target.setEstimated(source.isEstimated());
        target.setFreshnessStatus(source.getFreshnessStatus());
        target.setPersistenceStatus(source.getPersistenceStatus());
        return target;
    }

    private String dedupKey(RealtimeLoadPoint point) {
        return point.getDataSource() + "|" + point.getSourceInstanceId() + "|" + point.getSequence();
    }

    private long toEpochMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private LocalDateTime fromEpochMillis(long timestamp, LocalDateTime fallback) {
        return timestamp > 0 ? LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()) : fallback;
    }

    private void ensureInitialized() {
        if (!initialized) {
            currentLoad = 800;
            currentTemp = 25;
            currentHum = 60;
            targetLoad = 800;
            initialized = true;
        }
    }

    private float nextLoad() {
        if (demoMode == DemoMode.SPIKE) return approachTarget(DEMO_PEAK_LOAD, DEMO_STEP_MAX);
        if (demoMode == DemoMode.RECOVERY) {
            float next = approachTarget(targetLoad, DEMO_STEP_MAX);
            if (Math.abs(next - targetLoad) < 0.01f) demoMode = DemoMode.NORMAL;
            return next;
        }
        float delta = (targetLoad - currentLoad) * REVERSION_RATE + (float) random.nextGaussian() * NOISE_STDDEV;
        delta = Math.max(-STEP_MAX, Math.min(STEP_MAX, delta));
        return Math.max(0, currentLoad + delta);
    }

    private float approachTarget(float target, float maxStep) {
        float distance = target - currentLoad;
        return Math.abs(distance) <= maxStep ? target : currentLoad + Math.copySign(maxStep, distance);
    }

    private float nextTemperature() {
        return Math.max(-10, Math.min(45, currentTemp + (25f - currentTemp) * 0.001f + (float) random.nextGaussian() * 0.05f));
    }

    private float nextHumidity() {
        return Math.max(0, Math.min(100, currentHum + (60f - currentHum) * 0.001f + (float) random.nextGaussian() * 0.1f));
    }

    private RealtimeLoadStatus buildStatus() {
        float scenarioTarget = demoMode == DemoMode.SPIKE ? DEMO_PEAK_LOAD : targetLoad;
        return new RealtimeLoadStatus(demoMode.name(), currentLoad, scenarioTarget, targetLoad,
                SAFETY_THRESHOLD, SAFETY_THRESHOLD * 0.9f, SAFETY_THRESHOLD, SAFETY_THRESHOLD * 1.1f);
    }
}
