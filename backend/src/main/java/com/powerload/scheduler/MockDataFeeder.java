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
import java.util.Random;

/**
 * 模拟实时数据源 — 每分钟产出一条，波动幅度匹配真实负荷变化
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

    /**
     * 每分钟产出一条新数据。
     * 时间 = 上一行 + 1 分钟，值 = 小时模式 × 1000 + 随机 ±25MW
     */
    @Scheduled(fixedRate = 60_000)
    public void feed() {
        try {
            LoadData latest = loadDataService.getLatest();
            if (latest == null || latest.getLoadMw() == null) return;

            LocalDateTime nextTime = latest.getTime().plusMinutes(1);

            // 目标值：按当前小时的日模式曲线走
            float pattern = (float) HOURLY_PATTERN[nextTime.getHour()];
            float target = (float) (pattern * 1000);
            float prevLoad = latest.getLoadMw();

            // 向目标值靠近 + 随机波动
            float towardTarget = prevLoad + (target - prevLoad) * 0.05f;
            float noise = (float) random.nextGaussian() * 8f;
            float loadMw = Math.max(50, towardTarget + noise);

            float prevTemp = latest.getTemperature() != null ? latest.getTemperature() : 20;
            float prevHum = latest.getHumidity() != null ? latest.getHumidity() : 60;

            LoadData row = new LoadData();
            row.setTime(nextTime);
            row.setLoadMw(loadMw);
            row.setTemperature(prevTemp + (float) random.nextGaussian() * 0.2f);
            row.setHumidity((float) Math.max(0, Math.min(100, prevHum + random.nextGaussian() * 1)));
            row.setHour(nextTime.getHour());
            row.setDayOfWeek(nextTime.getDayOfWeek().getValue() % 7);
            row.setMonth(nextTime.getMonthValue());
            row.setIsHoliday(0);
            row.setCreatedAt(LocalDateTime.now());

            loadDataMapper.insert(row);
            pushService.pushLoad(row);
        } catch (Exception e) {
            if (!e.getMessage().contains("Duplicate")) {
                log.error("Feed error: {}", e.getMessage());
            }
        }
    }
}
