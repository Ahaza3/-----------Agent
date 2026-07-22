package com.powerload.dto.response;

import lombok.Data;

/**
 * 场景推演中的节点影响。
 */
@Data
public class GridScenarioNodeImpact {

    private Long nodeId;
    private String nodeCode;
    private String nodeName;
    private String nodeType;
    private Float currentLoadMw;
    private Float forecastPeakMw;
    private Float impactLoadMw;
    private String impactType;
}
