package com.powerload.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 预测响应 — 未来 24h 负荷预测
 */
@Data
public class ForecastResponse {

    /** 预测负荷值序列（24 个 float，单位 MW） */
    private List<Double> predictions;

    /** 模型名称 */
    private String model;
}
