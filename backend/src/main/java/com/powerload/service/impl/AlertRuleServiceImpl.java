package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerload.entity.AlertRule;
import com.powerload.mapper.AlertRuleMapper;
import com.powerload.service.AlertRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleServiceImpl implements AlertRuleService {

    private final AlertRuleMapper alertRuleMapper;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<AlertRule> listActive() {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRule::getIsActive, 1);
        return alertRuleMapper.selectList(wrapper);
    }

    @Override
    public List<AlertRule> listAll() {
        return alertRuleMapper.selectList(null);
    }

    @Override
    public AlertRule create(AlertRule rule) {
        validateConfig(rule);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        alertRuleMapper.insert(rule);
        log.info("告警规则已创建: id={}, name={}", rule.getId(), rule.getName());
        return rule;
    }

    @Override
    public AlertRule update(Long id, AlertRule rule) {
        validateConfig(rule);
        rule.setId(id);
        rule.setUpdatedAt(LocalDateTime.now());
        alertRuleMapper.updateById(rule);
        log.info("告警规则已更新: id={}", id);
        return alertRuleMapper.selectById(id);
    }

    /**
     * 校验 config JSON 中的阈值和比例
     */
    @SuppressWarnings("unchecked")
    private void validateConfig(AlertRule rule) {
        if (rule.getConfig() == null || rule.getConfig().isBlank()) {
            throw new IllegalArgumentException("规则配置不能为空");
        }
        try {
            Map<String, Object> cfg = objectMapper.readValue(rule.getConfig(), Map.class);
            // threshold > 0
            Object thresholdObj = cfg.get("threshold");
            if (thresholdObj == null) throw new IllegalArgumentException("threshold 不能为空");
            double threshold = ((Number) thresholdObj).doubleValue();
            if (threshold <= 0) throw new IllegalArgumentException("基准阈值必须大于 0");

            // 比例顺序校验
            Double yellowRatio = cfg.containsKey("yellowRatio") ? ((Number) cfg.get("yellowRatio")).doubleValue() : null;
            Double orangeRatio = cfg.containsKey("orangeRatio") ? ((Number) cfg.get("orangeRatio")).doubleValue() : null;
            Double redRatio = cfg.containsKey("redRatio") ? ((Number) cfg.get("redRatio")).doubleValue() : null;

            if (yellowRatio != null && orangeRatio != null && yellowRatio > orangeRatio) {
                throw new IllegalArgumentException("黄色比例不能大于橙色比例");
            }
            if (orangeRatio != null && redRatio != null && orangeRatio > redRatio) {
                throw new IllegalArgumentException("橙色比例不能大于红色比例");
            }

            // coolingTime >= 0
            if (cfg.containsKey("coolingTime")) {
                double coolingTime = ((Number) cfg.get("coolingTime")).doubleValue();
                if (coolingTime < 0) throw new IllegalArgumentException("冷却时间不能为负数");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("规则配置 JSON 格式无效: " + e.getMessage());
        }
    }
}
