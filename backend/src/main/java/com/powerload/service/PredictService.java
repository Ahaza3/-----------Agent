package com.powerload.service;

import com.powerload.dto.response.ForecastResponse;

/**
 * 负荷预测服务
 */
public interface PredictService {

    /**
     * 获取未来 24h 负荷预测
     *
     * <p>查询 DB 中最近 168h 的原始数据 → 发送到 Flask 推理 → 返回 24h 预测。</p>
     *
     * @return 预测响应（24 个负荷值 + 模型名称）
     */
    ForecastResponse forecast();
}
