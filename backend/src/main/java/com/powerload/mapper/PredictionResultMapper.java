package com.powerload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powerload.entity.PredictionResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预测结果 Mapper
 */
@Mapper
public interface PredictionResultMapper extends BaseMapper<PredictionResult> {
}
