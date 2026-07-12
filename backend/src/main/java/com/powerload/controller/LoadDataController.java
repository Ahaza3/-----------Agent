package com.powerload.controller;

import com.powerload.common.R;
import com.powerload.dto.response.LoadStats;
import com.powerload.entity.LoadData;
import com.powerload.service.LoadDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 负荷数据查询接口
 *
 * <p>提供历史负荷数据的范围查询、最新值查询和统计功能。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/data")
@RequiredArgsConstructor
public class LoadDataController {

    private final LoadDataService loadDataService;

    /**
     * 时间范围查询负荷数据
     *
     * @param start 起始时间（含），ISO 8601 格式
     * @param end   结束时间（不含），ISO 8601 格式
     * @return 按时间升序排列的负荷数据列表
     */
    @GetMapping("/range")
    public R<List<LoadData>> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start 不能晚于 end");
        }

        List<LoadData> data = loadDataService.queryRange(start, end);
        log.debug("范围查询: {} ~ {} → {} 条记录", start, end, data.size());
        return R.ok(data);
    }

    /**
     * 获取最新一条负荷数据
     *
     * @return 最新负荷记录
     */
    @GetMapping("/latest")
    public R<LoadData> latest() {
        LoadData data = loadDataService.getLatest();
        if (data == null) {
            return R.ok(null);
        }
        return R.ok(data);
    }

    /**
     * 负荷数据统计
     *
     * @param start 统计起始时间（含），ISO 8601 格式
     * @param end   统计结束时间（不含），ISO 8601 格式
     * @return 峰值、谷值、均值、负荷率、标准差等统计信息
     */
    @GetMapping("/stats")
    public R<LoadStats> stats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start 不能晚于 end");
        }

        LoadStats stats = loadDataService.getStats(start, end);
        return R.ok(stats);
    }
}
