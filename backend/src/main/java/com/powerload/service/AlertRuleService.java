package com.powerload.service;

import com.powerload.entity.AlertRule;

import java.util.List;

/**
 * 告警规则管理服务
 */
public interface AlertRuleService {

    /** 查询所有启用的规则 */
    List<AlertRule> listActive();

    /** 查询全部规则 */
    List<AlertRule> listAll();

    /** 创建规则 */
    AlertRule create(AlertRule rule);

    /** 更新规则 */
    AlertRule update(Long id, AlertRule rule);

    /** 暂挂规则指定分钟数 */
    AlertRule snooze(Long id, int minutes);

    /** 开启或关闭维护模式 */
    AlertRule setMaintenance(Long id, boolean enabled);
}
