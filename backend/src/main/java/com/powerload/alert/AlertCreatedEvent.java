package com.powerload.alert;

import com.powerload.entity.AlertEvent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/** 告警创建事件 — 由 AlertScheduler 发布，AlertAdviceService 异步消费 */
@Getter
public class AlertCreatedEvent extends ApplicationEvent {
    private final AlertEvent alert;

    public AlertCreatedEvent(Object source, AlertEvent alert) {
        super(source);
        this.alert = alert;
    }
}
