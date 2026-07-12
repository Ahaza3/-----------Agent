package com.powerload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powerload.entity.LoadData;
import org.apache.ibatis.annotations.Mapper;

/**
 * 历史负荷数据 Mapper
 */
@Mapper
public interface LoadDataMapper extends BaseMapper<LoadData> {
}
