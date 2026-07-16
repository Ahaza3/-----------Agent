package com.powerload.alert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertTemplateTest {

    private final AlertTemplate template = new AlertTemplate();

    @Test
    void formatsRiskPercentagesWithOneDecimalPlace() {
        assertTrue(template.generateAnalysis("YELLOW", 1020, 1100).contains("92.7%"));
        assertTrue(template.generateAnalysis("ORANGE", 1140, 1100).contains("103.6%"));
        assertTrue(template.generateAnalysis("RED", 1240, 1100).contains("12.7%"));
    }
}
