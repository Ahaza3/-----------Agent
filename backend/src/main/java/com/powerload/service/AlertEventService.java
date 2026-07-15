package com.powerload.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerload.entity.AlertEvent;

import java.time.LocalDateTime;

/**
 * 告警事件服务
 */
public interface AlertEventService {

    /**
     * 分页查询告警事件
     *
     * @param page  页码
     * @param size  每页条数
     * @param level 告警级别筛选（null=全部）
     * @param start 起始时间（null=不限）
     * @param end   结束时间（null=不限）
     * @param unreadOnly 仅查未读
     */
    Page<AlertEvent> query(int page, int size, String level,
                           LocalDateTime start, LocalDateTime end, boolean unreadOnly);

    /** 标记已读 */
    void markRead(Long id);

    /** 保存告警事件 */
    AlertEvent save(AlertEvent event);

    /**
     * 检查同一小时内同级别是否已有告警（去重）
     */
    boolean isDuplicate(LocalDateTime triggerTime, String level, Long ruleId);
}
