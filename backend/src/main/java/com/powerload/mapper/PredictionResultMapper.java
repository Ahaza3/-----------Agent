package com.powerload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powerload.dto.response.FixedLeadReviewCandidate;
import com.powerload.entity.PredictionResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预测结果 Mapper
 */
@Mapper
public interface PredictionResultMapper extends BaseMapper<PredictionResult> {

    @Select("""
            SELECT ranked.prediction_id AS predictionId, ranked.predict_time AS predictTime,
                   ranked.predicted_load AS predictedLoad, ranked.model_version_id AS modelVersionId,
                   ranked.model_version AS modelVersion, ranked.issued_at AS issuedAt
            FROM (
              SELECT pr.id AS prediction_id, pr.predict_time, pr.predicted_load, pr.model_version_id,
                     fr.model_version, fr.issued_at,
                     ROW_NUMBER() OVER (PARTITION BY pr.predict_time ORDER BY fr.issued_at DESC, pr.id DESC) AS row_num
              FROM prediction_result pr
              INNER JOIN forecast_run fr ON fr.id = pr.forecast_run_id
              WHERE fr.status = 'COMPLETED'
                AND pr.node_id = #{nodeId}
                AND pr.lead_hours = #{leadHour}
                AND fr.issued_at <= pr.predict_time
                AND pr.predict_time BETWEEN #{startTime} AND #{endTime}
                AND (#{modelVersion} IS NULL OR fr.model_version = #{modelVersion})
            ) ranked
            WHERE ranked.row_num = 1
            ORDER BY ranked.predict_time ASC, ranked.issued_at DESC, ranked.prediction_id DESC
            """)
    List<FixedLeadReviewCandidate> selectFixedLeadReviewCandidates(
            @Param("nodeId") Long nodeId, @Param("leadHour") int leadHour,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
            @Param("modelVersion") String modelVersion);
}
