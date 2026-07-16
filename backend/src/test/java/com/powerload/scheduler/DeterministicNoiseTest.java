package com.powerload.scheduler;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicNoiseTest {

    @Test
    void sameTimeShouldProduceSameNoise() {
        LocalDateTime t = LocalDateTime.of(2026, 7, 15, 12, 0);
        double n1 = DeterministicNoise.loadNoise(t);
        double n2 = DeterministicNoise.loadNoise(t);
        assertEquals(n1, n2, 1e-9, "同一时间噪声应可复现");
    }

    @Test
    void differentHoursShouldProduceDifferentNoise() {
        LocalDateTime t1 = LocalDateTime.of(2026, 7, 15, 12, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 7, 15, 13, 0);
        assertTrue(DeterministicNoise.noiseDiffers(t1, t2), "不同时间噪声应不同");
    }

    @Test
    void differentDaysShouldProduceDifferentNoise() {
        LocalDateTime t1 = LocalDateTime.of(2026, 7, 15, 12, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 7, 16, 12, 0);
        assertTrue(DeterministicNoise.noiseDiffers(t1, t2));
    }

    @Test
    void loadAndTempNoiseShouldBeDifferent() {
        LocalDateTime t = LocalDateTime.of(2026, 7, 15, 12, 0);
        double loadN = DeterministicNoise.loadNoise(t);
        double tempN = DeterministicNoise.tempNoise(t);
        assertNotEquals(loadN, tempN, 1e-9, "负荷与温度噪声应无关");
    }

    @Test
    void noiseShouldBeWithinReasonableRange() {
        LocalDateTime t = LocalDateTime.of(2026, 7, 15, 12, 0);
        // 3 sigma = ~90 MW for load noise
        double n = DeterministicNoise.loadNoise(t);
        assertTrue(Math.abs(n) < 150, "负荷噪声应在合理范围: " + n);

        double tn = DeterministicNoise.tempNoise(t);
        assertTrue(Math.abs(tn) < 15, "温度噪声应在合理范围: " + tn);

        double hn = DeterministicNoise.humNoise(t);
        assertTrue(Math.abs(hn) < 40, "湿度噪声应在合理范围: " + hn);
    }
}
