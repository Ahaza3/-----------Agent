package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 电网拓扑节点。
 *
 * <p>当前 MVP 使用区域-变电站-馈线三级逻辑拓扑。
 * allocationRatio 仅用于派生模拟数据，不代表真实潮流分配结果。</p>
 */
@Data
@TableName("grid_node")
public class GridNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String nodeCode;
    private String nodeName;
    private String nodeType;
    private Long parentId;
    private Float allocationRatio;
    private Float ratedCapacityMw;
    private String voltageLevel;
    private String status;
    private String topologyVersion;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
