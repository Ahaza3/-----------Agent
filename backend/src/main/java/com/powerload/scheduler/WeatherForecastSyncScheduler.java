package com.powerload.scheduler;

import com.powerload.service.WeatherForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherForecastSyncScheduler {

    private final WeatherForecastService weatherForecastService;

    @Scheduled(cron = "${weather.sync-cron:0 10 * * * *}")
    public void sync() {
        try {
            weatherForecastService.syncForecast();
        } catch (Exception e) {
            log.warn("定时同步未来天气失败: {}", e.getMessage());
        }
    }
}
