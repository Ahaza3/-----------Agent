package com.powerload.service;

import com.powerload.entity.AlertRule;
import com.powerload.mapper.AlertRuleMapper;
import com.powerload.service.impl.AlertRuleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertRuleValidationTest {

    @Mock(lenient = true)
    private AlertRuleMapper alertRuleMapper;

    @InjectMocks
    private AlertRuleServiceImpl alertRuleService;

    @BeforeEach
    void setUp() {
        lenient().when(alertRuleMapper.insert(any(AlertRule.class))).thenReturn(1);
    }

    @Test
    void shouldAcceptValidConfig() {
        AlertRule rule = new AlertRule();
        rule.setName("测试规则");
        rule.setType("THRESHOLD");
        rule.setConfig("{\"threshold\":1100,\"yellowRatio\":0.9,\"orangeRatio\":1.0,\"redRatio\":1.1,\"coolingTime\":3600}");

        assertDoesNotThrow(() -> alertRuleService.create(rule));
    }

    @Test
    void shouldRejectEmptyConfig() {
        AlertRule rule = new AlertRule();
        rule.setName("空配置规则");
        rule.setConfig("");

        assertThrows(IllegalArgumentException.class, () -> alertRuleService.create(rule));
    }

    @Test
    void shouldRejectNullConfig() {
        AlertRule rule = new AlertRule();
        rule.setName("空规则");

        assertThrows(IllegalArgumentException.class, () -> alertRuleService.create(rule));
    }

    @Test
    void shouldRejectThresholdZeroOrNegative() {
        AlertRule rule = new AlertRule();
        rule.setName("零阈值规则");
        rule.setConfig("{\"threshold\":0,\"yellowRatio\":0.9,\"orangeRatio\":1.0,\"redRatio\":1.1,\"coolingTime\":3600}");

        assertThrows(IllegalArgumentException.class, () -> alertRuleService.create(rule));
    }

    @Test
    void shouldRejectYellowGreaterThanOrange() {
        AlertRule rule = new AlertRule();
        rule.setName("比例颠倒规则");
        rule.setConfig("{\"threshold\":1100,\"yellowRatio\":1.2,\"orangeRatio\":1.0,\"redRatio\":1.3,\"coolingTime\":3600}");

        assertThrows(IllegalArgumentException.class, () -> alertRuleService.create(rule));
    }

    @Test
    void shouldRejectOrangeGreaterThanRed() {
        AlertRule rule = new AlertRule();
        rule.setName("比例颠倒规则2");
        rule.setConfig("{\"threshold\":1100,\"yellowRatio\":0.9,\"orangeRatio\":1.5,\"redRatio\":1.2,\"coolingTime\":3600}");

        assertThrows(IllegalArgumentException.class, () -> alertRuleService.create(rule));
    }

    @Test
    void shouldRejectNegativeCoolingTime() {
        AlertRule rule = new AlertRule();
        rule.setName("负冷却时间");
        rule.setConfig("{\"threshold\":1100,\"yellowRatio\":0.9,\"orangeRatio\":1.0,\"redRatio\":1.1,\"coolingTime\":-60}");

        assertThrows(IllegalArgumentException.class, () -> alertRuleService.create(rule));
    }

    @Test
    void shouldAcceptEqualRatios() {
        AlertRule rule = new AlertRule();
        rule.setName("单阈值规则");
        rule.setConfig("{\"threshold\":1000,\"yellowRatio\":1.0,\"orangeRatio\":1.0,\"redRatio\":1.0,\"coolingTime\":1800}");

        assertDoesNotThrow(() -> alertRuleService.create(rule));
    }

    @Test
    void shouldRejectInvalidJson() {
        AlertRule rule = new AlertRule();
        rule.setName("非法 JSON");
        rule.setConfig("not-a-json");

        assertThrows(IllegalArgumentException.class, () -> alertRuleService.create(rule));
    }

    @Test
    void shouldRejectMissingThreshold() {
        AlertRule rule = new AlertRule();
        rule.setName("缺少阈值");
        rule.setConfig("{\"yellowRatio\":0.9,\"orangeRatio\":1.0,\"redRatio\":1.1}");

        assertThrows(IllegalArgumentException.class, () -> alertRuleService.create(rule));
    }
}
