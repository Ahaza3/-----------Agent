package com.powerload.dto.response;

import lombok.Data;

/**
 * 拓扑连接关系展示对象。
 */
@Data
public class GridEdgeView {

    private Long id;
    private Long fromNodeId;
    private Long toNodeId;
    private String fromNodeCode;
    private String toNodeCode;
    private String edgeType;
    private Float capacityMw;
    private String status;
    private String topologyVersion;
}
