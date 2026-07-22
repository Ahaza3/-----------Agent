package com.powerload.dto.response;

import lombok.Data;

/**
 * 拓扑节点展示对象。
 */
@Data
public class GridNodeView {

    private Long id;
    private String nodeCode;
    private String nodeName;
    private String nodeType;
    private Long parentId;
    private String parentCode;
    private Float allocationRatio;
    private Float ratedCapacityMw;
    private String voltageLevel;
    private String status;
    private String topologyVersion;
    private Integer sortOrder;
}
