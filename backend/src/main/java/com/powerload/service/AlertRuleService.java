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
}
