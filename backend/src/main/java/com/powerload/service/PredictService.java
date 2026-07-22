package com.powerload.service;

import com.powerload.dto.response.ForecastResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 按指定拓扑节点执行 24 小时预测。
     */
    ForecastResponse forecast(Long nodeId);

    /**
     * 批量执行节点预测。单个节点失败不会阻断其他节点。
     */
    default Map<Long, ForecastResponse> forecastNodes(List<Long> nodeIds) {
        Map<Long, ForecastResponse> result = new LinkedHashMap<>();
        if (nodeIds == null) {
            return result;
        }
        for (Long nodeId : nodeIds) {
            if (nodeId == null) {
                continue;
            }
            try {
                ForecastResponse forecast = forecast(nodeId);
                if (forecast != null) {
                    result.put(nodeId, forecast);
                }
            } catch (RuntimeException ignored) {
                // 节点级预测允许部分节点暂时缺少数据，交由调用方记录失败。
            }
        }
        return result;
    }
}
