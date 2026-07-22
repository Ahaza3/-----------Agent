package com.powerload.dto.response;

import lombok.Data;

/**
 * 拓扑节点风险快照。
 */
@Data
public class GridRiskSnapshot {

    private Long nodeId;
    private String nodeCode;
    private String nodeName;
    private String nodeType;
    private String parentCode;
    private Float ratedCapacityMw;
    private Float currentLoadMw;
    private Float forecastPeakMw;
    private Float headroomMw;
    private String riskLevel;
    private String riskBasis;
    private boolean forecastAvailable;
    private boolean forecastAdjusted;
    private boolean simulated;
    private String source;
    private String riskReason;
    private String alertRootNodeCode;
    private boolean alertDeduplicated;
}
