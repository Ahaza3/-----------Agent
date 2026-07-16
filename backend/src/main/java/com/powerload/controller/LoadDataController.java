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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    /** 数据库存储的时区 */
    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 时间范围查询负荷数据
     *
     * @param start 起始时间（含），ISO 8601 格式（支持带时区 offset）
     * @param end   结束时间（不含），ISO 8601 格式（支持带时区 offset）
     * @return 按时间升序排列的负荷数据列表
     */
    @GetMapping("/range")
    public R<List<LoadData>> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {

        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start 不能晚于 end");
        }

        // 转换到 Asia/Shanghai 时区以匹配数据库 DATETIME 字段
        LocalDateTime startLdt = LocalDateTime.ofInstant(start, DB_ZONE);
        LocalDateTime endLdt = LocalDateTime.ofInstant(end, DB_ZONE);

        List<LoadData> data = loadDataService.queryRange(startLdt, endLdt);
        log.debug("范围查询: {} ~ {} → {} 条记录", startLdt, endLdt, data.size());
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
        return R.ok(data);
    }

    /**
     * 负荷数据统计
     *
     * @param start 统计起始时间（含），ISO 8601 格式（支持带时区 offset）
     * @param end   统计结束时间（不含），ISO 8601 格式（支持带时区 offset）
     * @return 峰值、谷值、均值、负荷率、标准差等统计信息
     */
    @GetMapping("/stats")
    public R<LoadStats> stats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {

        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start 不能晚于 end");
        }

        // 转换到 Asia/Shanghai 时区以匹配数据库 DATETIME 字段
        LocalDateTime startLdt = LocalDateTime.ofInstant(start, DB_ZONE);
        LocalDateTime endLdt = LocalDateTime.ofInstant(end, DB_ZONE);

        LoadStats stats = loadDataService.getStats(startLdt, endLdt);
        return R.ok(stats);
    }
}
