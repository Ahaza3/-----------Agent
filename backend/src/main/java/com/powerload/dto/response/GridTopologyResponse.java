package com.powerload.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 拓扑结构及其来源说明。
 */
@Data
public class GridTopologyResponse {

    private String topologyVersion;
    private boolean simulated;
    private String source;
    private List<GridNodeView> nodes;
    private List<GridEdgeView> edges;
}
