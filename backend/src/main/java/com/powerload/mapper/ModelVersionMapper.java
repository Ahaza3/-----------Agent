package com.powerload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powerload.entity.ModelVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 模型版本 Mapper
 */
@Mapper
public interface ModelVersionMapper extends BaseMapper<ModelVersion> {

    @Update("""
            UPDATE model_version
            SET is_active = CASE WHEN id = #{targetId} THEN 1 ELSE 0 END,
                runtime_status = CASE WHEN id = #{targetId} THEN 'ACTIVE' ELSE runtime_status END,
                deployed_at = CASE WHEN id = #{targetId} THEN #{deployedAt} ELSE deployed_at END,
                last_load_error = CASE WHEN id = #{targetId} THEN NULL ELSE last_load_error END,
                last_health_checked_at = CASE WHEN id = #{targetId} THEN #{deployedAt} ELSE last_health_checked_at END
            WHERE id = #{targetId} OR is_active = 1
            """)
    int publishAtomically(@Param("targetId") Long targetId, @Param("deployedAt") LocalDateTime deployedAt);
}
