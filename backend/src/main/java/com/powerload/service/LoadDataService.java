package com.powerload.service;

import com.powerload.dto.response.LoadStats;
import com.powerload.entity.LoadData;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 负荷数据查询服务
 */
public interface LoadDataService {

    /**
     * 时间范围查询负荷数据
     *
     * @param start 起始时间（含）
     * @param end   结束时间（不含）
     * @return 按时间升序排列的负荷数据列表
     */
    List<LoadData> queryRange(LocalDateTime start, LocalDateTime end);

    /**
     * 获取最新一条负荷数据
     *
     * @return 最新负荷记录，无数据时返回 null
     */
    LoadData getLatest();

    /**
     * 统计指定时间范围内的负荷数据
     *
     * @param start 起始时间（含）
     * @param end   结束时间（不含）
     * @return 负荷统计摘要
     */
    LoadStats getStats(LocalDateTime start, LocalDateTime end);
}
