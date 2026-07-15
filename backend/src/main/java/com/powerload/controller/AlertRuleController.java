package com.powerload.controller;

import com.powerload.common.R;
import com.powerload.entity.AlertRule;
import com.powerload.service.AlertRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警规则管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/alert/rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    /** 查询全部规则 */
    @GetMapping
    public R<List<AlertRule>> list() {
        return R.ok(alertRuleService.listAll());
    }

    /** 创建规则 */
    @PostMapping
    public R<AlertRule> create(@RequestBody AlertRule rule) {
        return R.ok(alertRuleService.create(rule));
    }

    /** 更新规则 */
    @PutMapping("/{id}")
    public R<AlertRule> update(@PathVariable Long id, @RequestBody AlertRule rule) {
        return R.ok(alertRuleService.update(id, rule));
    }
}
