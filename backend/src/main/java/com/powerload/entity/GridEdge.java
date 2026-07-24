package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 电网拓扑连接关系。
 */
@Data
@TableName("grid_edge")
public class GridEdge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fromNodeId;
    private Long toNodeId;
    private String edgeType;
    private Float capacityMw;
    private String status;
    private String topologyVersion;
    private LocalDateTime createdAt;
}
