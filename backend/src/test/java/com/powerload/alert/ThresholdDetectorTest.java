package com.powerload.alert;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ThresholdDetectorTest {

    private final ThresholdDetector detector = new ThresholdDetector();

    @Test
    void readsDurationAndHysteresisWithBackwardCompatibleDefaults() {
        assertEquals(15, detector.getTriggerDuration(
                "{\"threshold\":1100,\"triggerDuration\":15,\"hysteresis\":20}"));
        assertEquals(0, detector.getTriggerDuration("{\"threshold\":1100}"));
        assertEquals(20f, detector.getHysteresis(
                "{\"threshold\":1100,\"triggerDuration\":15,\"hysteresis\":20}"));
    }

    @Test
    void recoveryRequiresYellowThresholdMinusHysteresis() {
        String config = "{\"threshold\":1100,\"yellowRatio\":0.9,\"hysteresis\":20}";

        assertFalse(detector.canRecover(980f, config));
        assertTrue(detector.canRecover(970f, config));
    }

    @Test
    void maintenanceAndSnoozeSuppressNewAlerts() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 12, 0);

        assertTrue(detector.isSuppressed("{\"maintenance\":true}", now));
        assertTrue(detector.isSuppressed(
                "{\"suspendUntil\":\"2026-07-20T12:30\"}", now));
        assertFalse(detector.isSuppressed(
                "{\"suspendUntil\":\"2026-07-20T11:30\"}", now));
    }
}
