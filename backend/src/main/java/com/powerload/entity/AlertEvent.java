package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@TableName("alert_event")
public class AlertEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 触发时间 */
    private LocalDateTime triggerTime;


    private Long ruleId;

    /** AI 分析文案（模板生成） */
    private String aiAnalysis;

    /** 建议措施 */
    private String suggestion;


    private Integer isRead;

    /** 告警解决时间 */
    private LocalDateTime resolvedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
