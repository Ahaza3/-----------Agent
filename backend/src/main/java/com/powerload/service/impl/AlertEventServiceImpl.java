package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerload.entity.AlertEvent;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.service.AlertEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEventServiceImpl implements AlertEventService {

    private final AlertEventMapper alertEventMapper;

    @Override
    public Page<AlertEvent> query(int page, int size, String level,
                                  LocalDateTime start, LocalDateTime end, boolean unreadOnly) {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();

        if (level != null && !level.isEmpty()) {
            wrapper.eq(AlertEvent::getLevel, level);
        }
        if (start != null) {
            wrapper.ge(AlertEvent::getTriggerTime, start);
        }
        if (end != null) {
            wrapper.le(AlertEvent::getTriggerTime, end);
        }
        if (unreadOnly) {
            wrapper.eq(AlertEvent::getIsRead, 0);
        }
        wrapper.orderByDesc(AlertEvent::getTriggerTime);

        return alertEventMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public void markRead(Long id) {
        AlertEvent event = new AlertEvent();
        event.setId(id);
        event.setIsRead(1);
        alertEventMapper.updateById(event);
        log.debug("告警已读: id={}", id);
    }

    @Override
    public AlertEvent save(AlertEvent event) {
        alertEventMapper.insert(event);
        return event;
    }

    @Override
    public boolean isDuplicate(LocalDateTime triggerTime, String level, Long ruleId) {
        // 同一小时内的同级别 + 同规则视为重复
        LocalDateTime hourStart = triggerTime.withMinute(0).withSecond(0).withNano(0);
        LocalDateTime hourEnd = hourStart.plusHours(1);

        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertEvent::getLevel, level)
               .eq(AlertEvent::getRuleId, ruleId)
               .ge(AlertEvent::getTriggerTime, hourStart)
               .lt(AlertEvent::getTriggerTime, hourEnd);

        return alertEventMapper.selectCount(wrapper) > 0;
    }
}
