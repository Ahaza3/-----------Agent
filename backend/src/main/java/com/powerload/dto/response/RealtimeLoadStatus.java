package com.powerload.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 实时负荷模拟器状态，仅供开发环境演示控制使用。 */
@Data
@AllArgsConstructor
public class RealtimeLoadStatus {

    private String mode;
    private float currentLoad;
    private float targetLoad;
    private float normalTargetLoad;
    private float safetyThreshold;
    private float yellowThreshold;
    private float orangeThreshold;
    private float redThreshold;
}
