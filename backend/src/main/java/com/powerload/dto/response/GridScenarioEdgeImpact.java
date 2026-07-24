package com.powerload.dto.response;

import lombok.Data;

/**
 * 场景推演中的线路影响。
 */
@Data
public class GridScenarioEdgeImpact {

    private Long edgeId;
    private String fromNodeCode;
    private String toNodeCode;
    private String edgeType;
    private Float capacityMw;
    private String impactType;
}
