package com.powerload.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 拓扑故障影响场景结果。
 */
@Data
public class GridScenarioResponse {

    private String targetType;
    private Long targetId;
    private String targetCode;
    private String targetName;
    private String severity;
    private Float baselineLoadMw;
    private Float affectedLoadMw;
    private Float transferableHeadroomMw;
    private Float unservedLoadMw;
    private boolean simulated;
    private String source;
    private List<GridScenarioNodeImpact> affectedNodes;
    private List<GridScenarioEdgeImpact> affectedEdges;
    private List<String> assumptions;
}
