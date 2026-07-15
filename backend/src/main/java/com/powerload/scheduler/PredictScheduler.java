package com.powerload.scheduler;

import com.powerload.dto.response.ForecastResponse;
import com.powerload.service.PredictService;
import com.powerload.websocket.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 预测调度 — 每小时触发一次预测，结果通过 WebSocket 推送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PredictScheduler {

    private final PredictService predictService;
    private final PushService pushService;

    @Scheduled(fixedRate = 3_600_000) // 1 小时
    public void pushPrediction() {
        try {
            ForecastResponse forecast = predictService.forecast();
            pushService.pushPrediction(forecast);
            log.info("定时预测完成: {} values", forecast.getPredictions().size());
        } catch (Exception e) {
            log.error("预测调度异常", e);
        }
    }
}
