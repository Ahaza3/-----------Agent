package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.entity.AlertRule;
import com.powerload.mapper.AlertRuleMapper;
import com.powerload.service.AlertRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleServiceImpl implements AlertRuleService {

    private final AlertRuleMapper alertRuleMapper;

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
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        alertRuleMapper.insert(rule);
        log.info("告警规则已创建: id={}, name={}", rule.getId(), rule.getName());
        return rule;
    }

    @Override
    public AlertRule update(Long id, AlertRule rule) {
        rule.setId(id);
        rule.setUpdatedAt(LocalDateTime.now());
        alertRuleMapper.updateById(rule);
        log.info("告警规则已更新: id={}", id);
        return alertRuleMapper.selectById(id);
    }
}
