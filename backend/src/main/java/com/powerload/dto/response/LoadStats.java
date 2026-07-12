package com.powerload.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 负荷统计响应 — 时间范围内的负荷统计摘要
 */
@Data
public class LoadStats {

    /** 峰值负荷 (MW) */
    private Float peakLoad;

    /** 峰值时间 */
    private LocalDateTime peakTime;

    /** 谷值负荷 (MW) */
    private Float valleyLoad;

    /** 谷值时间 */
    private LocalDateTime valleyTime;

    /** 平均负荷 (MW) */
    private Float avgLoad;

    /** 负荷率 = 均值 / 峰值 */
    private Float loadRate;

    /** 标准差 */
    private Float stdDeviation;

    /** 统计数据点数 */
    private Integer dataPoints;
}
