package com.powerload.controller;

import com.powerload.common.R;
import com.powerload.dto.response.ForecastResponse;
import com.powerload.service.PredictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 负荷预测接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/predict")
@RequiredArgsConstructor
public class PredictController {

    private final PredictService predictService;

    /**
     * 获取未来 24 小时负荷预测
     *
     * <p>链式调用: DB → 特征工程(Flask) → LSTM 推理 → 返回 24 个负荷值</p>
     *
     * @return 预测响应（24 个预测值 + 模型名称）
     */
    @GetMapping("/forecast")
    public R<ForecastResponse> forecast() {
        ForecastResponse data = predictService.forecast();
        return R.ok(data);
    }
}
