package com.powerload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powerload.entity.RealtimeTelemetry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface RealtimeTelemetryMapper extends BaseMapper<RealtimeTelemetry> {
    @Delete("DELETE FROM realtime_telemetry WHERE received_at < #{cutoff} LIMIT #{limit}")
    int deleteExpiredBatch(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
