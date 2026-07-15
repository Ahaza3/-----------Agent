package com.powerload.scheduler;

import com.powerload.entity.LoadData;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.service.LoadDataService;
import com.powerload.websocket.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;

/**
 * 模拟实时数据源 — 每 5 秒产出一条新数据并直接推送 WebSocket
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockDataFeeder {

    private final LoadDataMapper loadDataMapper;
    private final LoadDataService loadDataService;
    private final PushService pushService;
    private final Random random = new Random();

    private static final double[] HOURLY_PATTERN = {
        0.65, 0.60, 0.58, 0.55, 0.52, 0.55, 0.62, 0.75,
        0.88, 0.95, 1.00, 0.93, 0.85, 0.82, 0.88, 0.92,
        0.98, 1.00, 0.96, 0.90, 0.85, 0.80, 0.75, 0.70,
    };

    @Scheduled(fixedRate = 5_000)
    public void feed() {
        try {
            LoadData latest = loadDataService.getLatest();
            if (latest == null || latest.getLoadMw() == null) return;

            // 从最后一条的时间往后推 5 秒
            LocalDateTime nextTime = latest.getTime().plusSeconds(5);

            // 负荷：小时模式 + 微调
            float pattern = (float) HOURLY_PATTERN[nextTime.getHour()];
            float prevLoad = latest.getLoadMw();
            float delta = (float) random.nextGaussian() * 2f;
            float loadMw = (float) (prevLoad * 0.90 + pattern * 1000 * 0.10) + delta;
            loadMw = Math.max(50, loadMw);

            float prevTemp = latest.getTemperature() != null ? latest.getTemperature() : 20;
            float prevHum = latest.getHumidity() != null ? latest.getHumidity() : 60;

            LoadData row = new LoadData();
            row.setTime(nextTime);
            row.setLoadMw(loadMw);
            row.setTemperature(prevTemp + (float) random.nextGaussian() * 0.1f);
            row.setHumidity((float) Math.max(0, Math.min(100, prevHum + random.nextGaussian() * 0.5)));
            row.setHour(nextTime.getHour());
            row.setDayOfWeek(nextTime.getDayOfWeek().getValue() % 7);
            row.setMonth(nextTime.getMonthValue());
            row.setIsHoliday(0);
            row.setCreatedAt(LocalDateTime.now());

            loadDataMapper.insert(row);

            // 写入后立即推送，零延迟
            pushService.pushLoad(row);
        } catch (Exception e) {
            if (!e.getMessage().contains("Duplicate")) {
                log.error("Feed error: {}", e.getMessage());
            }
        }
    }
}
