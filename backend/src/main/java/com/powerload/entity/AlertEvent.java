package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警事件表 — alert_event
 */
@Data
@TableName("alert_event")
public class AlertEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 触发时间 */
    private LocalDateTime triggerTime;

    /** 告警级别: RED/ORANGE/YELLOW */
    private String level;

    /** 告警类型: THRESHOLD/TREND/ANOMALY */
    private String type;

    /** 当前负荷值(MW) */
    private Float currentValue;

    /** 触发阈值(MW) */
    private Float thresholdValue;

    /** 关联告警规则ID */
    private Long ruleId;

    /** AI 分析文案（模板生成） */
    private String aiAnalysis;

    /** 建议措施 */
    private String suggestion;

    /** 是否已读(0=未读,1=已读) */
    private Integer isRead;

    /** 告警解决时间 */
    private LocalDateTime resolvedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
