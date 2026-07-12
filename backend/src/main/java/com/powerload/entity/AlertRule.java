package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警规则实体 — 对应 alert_rule 表
 *
 * <p>config 字段为 JSON 格式，包含 threshold / redRatio / orangeRatio / yellowRatio / coolingTime。
 *
 * <pre>{@code
 * {
 *   "threshold": 1200,
 *   "redRatio": 1.10,
 *   "orangeRatio": 1.00,
 *   "yellowRatio": 0.90,
 *   "coolingTime": 3600
 * }
 * }</pre>
 * </p>
 */
@Data
@TableName("alert_rule")
public class AlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则名称 */
    private String name;

    /** 规则类型 */
    private String type;

    /** 规则配置 (JSON 格式) */
    private String config;

    /** 是否启用 (0=禁用, 1=启用) */
    private Integer isActive;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
