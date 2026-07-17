package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史负荷数据实体 — 对应 load_data 表
 *
 * <p>存储逐小时负荷数据，是预测模型的唯一数据源。
 * hour / dayOfWeek / month 为冗余字段，加速聚合查询。</p>
 */
@Data
@TableName("load_data")
public class LoadData {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据时间点（精确到小时） */
    private LocalDateTime time;

    /** 负荷值 (MW) */
    private Float loadMw;

    /** 温度 (°C) */
    private Float temperature;

    /** 湿度 (%) */
    private Float humidity;

    /** 是否节假日 (0=否, 1=是) */
    private Integer isHoliday;

    /** 小时 (0-23) */
    private Integer hour;

    /** 星期几 (0=周一, 6=周日) */
    private Integer dayOfWeek;

    /** 月份 (1-12) */
    private Integer month;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 数据来源（MOCK_HISTORY / RECOVERED_SIMULATION） */
    private String dataSource;
}
