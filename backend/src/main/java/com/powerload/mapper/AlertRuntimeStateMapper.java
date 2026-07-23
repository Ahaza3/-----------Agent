package com.powerload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powerload.entity.AlertRuntimeState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AlertRuntimeStateMapper extends BaseMapper<AlertRuntimeState> {
    @Select("SELECT * FROM alert_runtime_state WHERE state_key = #{stateKey} FOR UPDATE")
    AlertRuntimeState selectByStateKeyForUpdate(@Param("stateKey") String stateKey);
}
