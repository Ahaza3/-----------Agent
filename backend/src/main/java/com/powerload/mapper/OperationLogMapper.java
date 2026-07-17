package com.powerload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powerload.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
