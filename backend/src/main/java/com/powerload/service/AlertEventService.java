package com.powerload.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerload.entity.AlertEvent;
import com.powerload.security.SysUserPrincipal;
import com.powerload.dto.request.AlertDeliveryAckRequest;
import com.powerload.dto.response.AlertDeliveryMetricsResponse;
import com.powerload.entity.AlertDeliveryMetric;

import java.time.LocalDateTime;
import java.util.Map;

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

    /**
     * 按级别、来源、状态、关键词和时间范围分页查询告警。
     */
    default Page<AlertEvent> query(int page, int size, String level, String type,
                                   String status, String keyword,
                                   LocalDateTime start, LocalDateTime end,
                                   boolean unreadOnly) {
        return query(page, size, level, start, end, unreadOnly);
    }

    /** 标记已读 */
    void markRead(Long id);

    /** 保存告警事件 */
    AlertEvent save(AlertEvent event);

    /**
     * 检查同一小时内同级别是否已有告警（去重）
     */
    boolean isDuplicate(LocalDateTime triggerTime, String level, Long ruleId);

    /**
     * 节点级告警去重：同一节点、同一类型、同一级别、同一规则在一小时内只保留一次。
     */
    boolean isDuplicate(LocalDateTime triggerTime, String level, Long ruleId, Long nodeId, String type);

    void acknowledge(Long id, SysUserPrincipal user);

    void resolveLatest(Long ruleId, LocalDateTime resolvedAt);

    /**
     * 恢复指定节点最近一条未恢复告警。
     */
    void resolveLatest(Long ruleId, Long nodeId, String type, LocalDateTime resolvedAt);

    /** 查询指定时间范围内的告警运营指标 */
    Map<String, Object> metrics(LocalDateTime start, LocalDateTime end);

    AlertDeliveryMetric acknowledgeDelivery(Long alertId, SysUserPrincipal user, AlertDeliveryAckRequest request);

    AlertDeliveryMetricsResponse deliveryMetrics(LocalDateTime start, LocalDateTime end, Long nodeId);
}
