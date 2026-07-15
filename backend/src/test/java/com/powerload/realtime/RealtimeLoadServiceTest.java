package com.powerload.realtime;

import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.dto.response.RealtimeLoadStatus;
import com.powerload.service.RealtimeLoadService;
import com.powerload.service.impl.RealtimeLoadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RealtimeLoadService 单元测试
 */
class RealtimeLoadServiceTest {

    private RealtimeLoadService service;
    private Random fixedRandom;

    @BeforeEach
    void setUp() {
        fixedRandom = new Random(42); // 固定种子，可复现
        service = new RealtimeLoadServiceImpl(fixedRandom);
        service.initialize(800, 25, 60);
    }

    @Test
    @DisplayName("1. 连续生成 60 个实时点，timestamp 和 sequence 严格递增")
    void testGenerate60Points_monotonic() {
        long prevSeq = -1;
        long prevTs = -1;

        for (int i = 0; i < 60; i++) {
            RealtimeLoadPoint p = service.generateAndAppend();
            assertTrue(p.getSequence() > prevSeq, "sequence should increase: " + p.getSequence() + " > " + prevSeq);
            assertTrue(p.getTimestamp() >= prevTs, "timestamp should be non-decreasing: " + p.getTimestamp() + " >= " + prevTs);
            assertNotNull(p.getSource());
            assertEquals("MOCK", p.getSource());
            prevSeq = p.getSequence();
            prevTs = p.getTimestamp();
        }
        assertEquals(60, service.size());
    }

    @Test
    @DisplayName("2. 相邻负荷变化不超过 3 MW 上限")
    void testStepChangeWithinLimit() {
        float prevLoad = 800;
        for (int i = 0; i < 100; i++) {
            RealtimeLoadPoint p = service.generateAndAppend();
            float delta = Math.abs(p.getLoadMw() - prevLoad);
            assertTrue(delta <= 3.0f, "step change " + delta + " should be <= 3 MW at step " + i);
            prevLoad = p.getLoadMw();
        }
    }

    @Test
    @DisplayName("3. 负荷不会小于 0")
    void testLoadNonNegative() {
        for (int i = 0; i < 200; i++) {
            RealtimeLoadPoint p = service.generateAndAppend();
            assertTrue(p.getLoadMw() >= 0, "load should be >= 0, got " + p.getLoadMw() + " at step " + i);
        }
    }

    @Test
    @DisplayName("4. 环形缓存超过 3600 容量后正确淘汰最旧数据")
    void testRingBufferEviction() {
        // Fill to max capacity
        for (int i = 0; i < 3600; i++) {
            service.generateAndAppend();
        }
        assertEquals(3600, service.size());
        long firstSeq = service.getRecent(60).get(0).getSequence();

        // Add one more — should evict the oldest
        RealtimeLoadPoint extra = service.generateAndAppend();
        assertEquals(3600, service.size());
        long newFirstSeq = service.getRecent(60).get(0).getSequence();
        assertTrue(newFirstSeq > firstSeq, "oldest should have been evicted");
    }

    @Test
    @DisplayName("5. getRecent 返回有序数据")
    void testGetRecent_ordered() {
        for (int i = 0; i < 100; i++) {
            service.generateAndAppend();
        }

        List<RealtimeLoadPoint> recent = service.getRecent(60);
        assertFalse(recent.isEmpty(), "should have recent data");

        long prevSeq = -1;
        for (RealtimeLoadPoint p : recent) {
            assertTrue(p.getSequence() > prevSeq, "points should be ordered by sequence");
            prevSeq = p.getSequence();
        }
    }

    @Test
    @DisplayName("6. getRecent 返回防御性副本 — 修改不影响内部缓存")
    void testGetRecent_defensiveCopy() {
        for (int i = 0; i < 10; i++) {
            service.generateAndAppend();
        }

        List<RealtimeLoadPoint> recent = service.getRecent(60);
        int sizeBefore = recent.size();
        recent.clear(); // mutate the returned list

        List<RealtimeLoadPoint> recentAfter = service.getRecent(60);
        assertEquals(sizeBefore, recentAfter.size(), "internal buffer should not be affected by external mutation");
    }

    @Test
    @DisplayName("7. getLatest 返回最新点")
    void testGetLatest() {
        RealtimeLoadPoint p1 = service.generateAndAppend();
        RealtimeLoadPoint latest = service.getLatest();
        assertEquals(p1.getSequence(), latest.getSequence());

        RealtimeLoadPoint p2 = service.generateAndAppend();
        latest = service.getLatest();
        assertEquals(p2.getSequence(), latest.getSequence());
    }

    @Test
    @DisplayName("8. 演示突增在 25 秒内达到 1240MW")
    void testSpikeDemoReachesPeak() {
        service.initialize(940, 25, 60);
        service.startSpikeDemo();

        RealtimeLoadPoint point = null;
        for (int i = 0; i < 25; i++) {
            point = service.generateAndAppend();
        }

        assertNotNull(point);
        assertEquals(1240f, point.getLoadMw(), 0.01f);
        assertEquals("MOCK_DEMO", point.getSource());
        assertEquals("SPIKE", service.getStatus().getMode());
    }

    @Test
    @DisplayName("9. 演示恢复回到正常基线后自动退出演示模式")
    void testRecoveryReturnsToNormal() {
        service.initialize(940, 25, 60);
        service.startSpikeDemo();
        for (int i = 0; i < 25; i++) service.generateAndAppend();

        service.startRecoveryDemo();
        for (int i = 0; i < 25; i++) service.generateAndAppend();
        RealtimeLoadStatus status = service.getStatus();

        assertEquals(940f, status.getCurrentLoad(), 0.01f);
        assertEquals("NORMAL", status.getMode());
    }
}
