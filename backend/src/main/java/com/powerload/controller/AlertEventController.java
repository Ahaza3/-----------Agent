package com.powerload.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerload.alert.AlertJudgementService;
import com.powerload.common.R;
import com.powerload.dto.response.AlertJudgementResult;
import com.powerload.entity.AlertAdvice;
import com.powerload.entity.AlertEvent;
import com.powerload.mapper.AlertAdviceMapper;
import com.powerload.mapper.AlertEventMapper;
import com.powerload.service.AlertEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警事件查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/alert")
@RequiredArgsConstructor
public class AlertEventController {

    private final AlertEventService alertEventService;
    private final AlertAdviceMapper alertAdviceMapper;
    private final AlertEventMapper alertEventMapper;
    private final AlertJudgementService alertJudgementService;

    /**
     * 查询某条告警的 AI 角色化建议
     */
    @GetMapping("/events/{id}/advice")
    public R<List<AlertAdvice>> getAdvice(@PathVariable Long id) {
        var wrapper = new LambdaQueryWrapper<AlertAdvice>()
                .eq(AlertAdvice::getAlertId, id)
                .orderByAsc(AlertAdvice::getAudienceRole);
        return R.ok(alertAdviceMapper.selectList(wrapper));
    }

    /**
     * 分页查询告警事件
     *
     * @param level      级别筛选 (RED/ORANGE/YELLOW)，不传则全部
     * @param start      起始时间
     * @param end        结束时间
     * @param page       页码 (从 1 开始)
     * @param size       每页条数
     * @param unreadOnly 仅查未读
     */
    @GetMapping("/events")
    public R<Map<String, Object>> list(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {

        Page<AlertEvent> result = alertEventService.query(page, size, level, start, end, unreadOnly);
        return R.ok(Map.of(
                "records", result.getRecords(),
                "total", result.getTotal(),
                "page", result.getCurrent(),
                "size", result.getSize(),
                "pages", result.getPages()
        ));
    }

    /** 标记告警已读 */
    @PutMapping("/events/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        alertEventService.markRead(id);
        return R.ok();
    }

    /** 查询告警智能研判（GET — 已有则返回，无则生成一次） */
    @GetMapping("/events/{id}/judgement")
    public R<AlertJudgementResult> getJudgement(@PathVariable Long id) {
        AlertJudgementResult existing = alertJudgementService.getExistingJudgement(id);
        if (existing != null) return R.ok(existing);
        AlertEvent event = alertEventMapper.selectById(id);
        if (event == null) throw new IllegalArgumentException("告警不存在: " + id);
        return R.ok(alertJudgementService.judge(event));
    }

    /** 手动重新研判（POST — 幂等，不重复建单） */
    @PostMapping("/events/{id}/judgement")
    @PreAuthorize("hasAnyRole('DISPATCHER','SYSTEM_ADMIN')")
    public R<AlertJudgementResult> rejudge(@PathVariable Long id) {
        AlertEvent event = alertEventMapper.selectById(id);
        if (event == null) throw new IllegalArgumentException("告警不存在: " + id);
        return R.ok(alertJudgementService.judge(event));
    }
}
