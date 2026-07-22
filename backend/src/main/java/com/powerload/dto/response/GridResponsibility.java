package com.powerload.dto.response;

import lombok.Data;

/**
 * 告警责任域解析结果。
 *
 * <p>责任域只由拓扑父链计算，模型不得改变路由结论。</p>
 */
@Data
public class GridResponsibility {

    private Long sourceNodeId;
    private String sourceNodeCode;
    private String sourceNodeName;
    private String substationCode;
    private String substationName;
    private Long assigneeUserId;
    private String assigneeName;
    private boolean dispatchCenter;
    private String routeReason;
}
