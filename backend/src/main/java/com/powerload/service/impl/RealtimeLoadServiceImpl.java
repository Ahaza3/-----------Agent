package com.powerload.service.impl;

import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.dto.response.RealtimeLoadStatus;
import com.powerload.service.RealtimeLoadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 实时负荷服务实现 — 线程安全环形缓存 + 均值回归模拟器
 *
 * <p>负荷生成采用均值回归随机游走（Ornstein-Uhlenbeck 过程简化版）：</p>
 * <pre>
 *   delta = (targetLoad - currentLoad) × reversionRate + gaussianNoise
 *   nextLoad = clamp(currentLoad + delta, 0, +∞)
 *   |delta| ≤ 3 MW 每步
 * </pre>
 *
 * <p>温度/湿度也做小幅度随机游走，保持在一个合理区间。</p>
 */
@Slf4j
@Service
public class RealtimeLoadServiceImpl implements RealtimeLoadService {

    /* ─── 环形缓存 ─── */
    private final List<RealtimeLoadPoint> buffer = new ArrayList<>(3600);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final int MAX_CAPACITY = 3600;

    /* ─── 单调序号 ─── */
    private final AtomicLong sequence = new AtomicLong(0);

    /* ─── 模拟器状态 ─── */
    private float currentLoad;
    private float currentTemp;
    private float currentHum;
    private float targetLoad;
    private boolean initialized;
    private DemoMode demoMode = DemoMode.NORMAL;

    /* ─── 随机源（可注入，方便测试） ─── */
    private final Random random;

    /* ─── 参数 ─── */
    private static final float STEP_MAX = 3.0f;        // 单秒最大变化 MW
    private static final float REVERSION_RATE = 0.005f; // 均值回归速率（每步靠近 0.5%）
    private static final float NOISE_STDDEV = 1.0f;     // 高斯噪声标准差 MW
    private static final float DEMO_STEP_MAX = 12.0f;
    private static final float SAFETY_THRESHOLD = 1100f;
    private static final float DEMO_PEAK_LOAD = 1240f;

    private enum DemoMode {
        NORMAL,
        SPIKE,
        RECOVERY
    }

    public RealtimeLoadServiceImpl() {
        this.random = new Random();
    }

    /** 测试用：注入固定种子 Random */
    public RealtimeLoadServiceImpl(Random random) {
        this.random = random;
    }

    /* ══════════════════════════════════════════════
     *  初始化
     * ══════════════════════════════════════════════ */

    @Override
    public void initialize(float initialLoad, float initialTemp, float initialHum) {
        lock.writeLock().lock();
        try {
            this.currentLoad = initialLoad;
            this.currentTemp = initialTemp;
            this.currentHum = initialHum;
            this.targetLoad = initialLoad;
            this.demoMode = DemoMode.NORMAL;
            this.initialized = true;
            log.info("实时模拟器初始化: load={}MW, temp={}°C, hum={}%", initialLoad, initialTemp, initialHum);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /* ══════════════════════════════════════════════
     *  生成与追加
     * ══════════════════════════════════════════════ */

    @Override
    public RealtimeLoadPoint generateAndAppend() {
        lock.writeLock().lock();
        try {
            if (!initialized) {
                // 未初始化时使用默认值
                currentLoad = 800;
                currentTemp = 25;
                currentHum = 60;
                targetLoad = 800;
                initialized = true;
                log.warn("实时模拟器未显式初始化，使用默认值: 800MW");
            }

            float newLoad = nextLoad();
            float newTemp = nextTemperature();
            float newHum = nextHumidity();

            RealtimeLoadPoint point = new RealtimeLoadPoint();
            point.setTimestamp(System.currentTimeMillis());
            point.setSequence(sequence.incrementAndGet());
            point.setLoadMw(newLoad);
            point.setTemperature(newTemp);
            point.setHumidity(newHum);
            point.setSource(demoMode == DemoMode.NORMAL ? "MOCK" : "MOCK_DEMO");

            buffer.add(point);
            if (buffer.size() > MAX_CAPACITY) {
                buffer.remove(0); // 淘汰最旧
            }

            currentLoad = newLoad;
            currentTemp = newTemp;
            currentHum = newHum;

            return point;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /* ─── 均值回归随机游走 ─── */

    private float nextLoad() {
        if (demoMode == DemoMode.SPIKE) {
            return approachTarget(DEMO_PEAK_LOAD, DEMO_STEP_MAX);
        }
        if (demoMode == DemoMode.RECOVERY) {
            float next = approachTarget(targetLoad, DEMO_STEP_MAX);
            if (Math.abs(next - targetLoad) < 0.01f) {
                demoMode = DemoMode.NORMAL;
            }
            return next;
        }

        float reversion = (targetLoad - currentLoad) * REVERSION_RATE;
        float noise = (float) random.nextGaussian() * NOISE_STDDEV;
        float delta = reversion + noise;
        // 钳制单步变化
        if (delta > STEP_MAX) delta = STEP_MAX;
        if (delta < -STEP_MAX) delta = -STEP_MAX;
        float next = currentLoad + delta;
        if (next < 0) next = 0;
        return next;
    }

    private float approachTarget(float target, float maxStep) {
        float distance = target - currentLoad;
        if (Math.abs(distance) <= maxStep) {
            return target;
        }
        return currentLoad + Math.copySign(maxStep, distance);
    }

    private float nextTemperature() {
        // 温度缓慢变化，趋向一个温和区间
        float ideal = 25f;
        float reversion = (ideal - currentTemp) * 0.001f;
        float noise = (float) random.nextGaussian() * 0.05f;
        float next = currentTemp + reversion + noise;
        if (next < -10) next = -10;
        if (next > 45) next = 45;
        return next;
    }

    private float nextHumidity() {
        float ideal = 60f;
        float reversion = (ideal - currentHum) * 0.001f;
        float noise = (float) random.nextGaussian() * 0.1f;
        float next = currentHum + reversion + noise;
        if (next < 0) next = 0;
        if (next > 100) next = 100;
        return next;
    }

    /**
     * 更新目标负荷（可用于跟随小时级 DB 变化趋势）
     */
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

    private void ensureInitialized() {
        if (!initialized) {
            currentLoad = 800;
            currentTemp = 25;
            currentHum = 60;
            targetLoad = 800;
            initialized = true;
        }
    }

    private RealtimeLoadStatus buildStatus() {
        float scenarioTarget = switch (demoMode) {
            case SPIKE -> DEMO_PEAK_LOAD;
            case NORMAL, RECOVERY -> targetLoad;
        };
        return new RealtimeLoadStatus(
                demoMode.name(),
                currentLoad,
                scenarioTarget,
                targetLoad,
                SAFETY_THRESHOLD,
                SAFETY_THRESHOLD * 0.9f,
                SAFETY_THRESHOLD,
                SAFETY_THRESHOLD * 1.1f);
    }

    /* ══════════════════════════════════════════════
     *  查询
     * ══════════════════════════════════════════════ */

    @Override
    public RealtimeLoadPoint getLatest() {
        lock.readLock().lock();
        try {
            if (buffer.isEmpty()) return null;
            return buffer.get(buffer.size() - 1);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<RealtimeLoadPoint> getRecent(int minutes) {
        lock.readLock().lock();
        try {
            long cutoff = System.currentTimeMillis() - minutes * 60_000L;
            List<RealtimeLoadPoint> result = new ArrayList<>();
            for (RealtimeLoadPoint p : buffer) {
                if (p.getTimestamp() >= cutoff) {
                    result.add(p);
                }
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
}
