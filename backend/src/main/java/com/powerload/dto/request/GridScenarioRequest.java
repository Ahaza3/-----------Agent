package com.powerload.dto.request;

import lombok.Data;

/**
 * 拓扑故障场景请求。
 */
@Data
public class GridScenarioRequest {

    /**
     * NODE 或 EDGE。
     */
    private String targetType;

    private Long nodeId;

    private Long edgeId;
}
