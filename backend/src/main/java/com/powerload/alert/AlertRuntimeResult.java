package com.powerload.alert;

import com.powerload.entity.AlertEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AlertRuntimeResult {
    private final AlertEvent createdEvent;

    public static AlertRuntimeResult unchanged() {
        return new AlertRuntimeResult(null);
    }
}
