package com.powerload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powerload.entity.GridNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 电网拓扑节点 Mapper。
 */
@Mapper
public interface GridNodeMapper extends BaseMapper<GridNode> {
}
