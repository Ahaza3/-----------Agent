package com.powerload.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预测响应 — 未来 24h 负荷预测
 *
 * <p>forecastStartTime 由后端指定，前端直接使用，不再根据本地时钟推算预测起点。</p>
 */
@Data
public class ForecastResponse {

    /** 预测负荷值序列（24 个 float，单位 MW） */
    private List<Double> predictions;

    /** 模型名称 */
    private String model;

    /** 预测基准时间 — 第一个预测值对应的时间（Asia/Shanghai） */
    private LocalDateTime forecastStartTime;

    private List<Double> lowerBounds;

    private List<Double> upperBounds;

    private String intervalSource;

    private Long modelVersionId;

    private boolean futureWeatherAvailable;

    private String weatherSource;

    private boolean futureWeatherApplied;

    private boolean futureWeatherFallback;
}
