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
 * 模拟实时数据源
 *
 * <p>只插入小时级数据，与历史粒度一致。
 * 每 30 秒检查一次：如果有缺口（最新整点 < 当前整点），补一条小时数据。</p>
 *
 * <p>当前负荷的实时读数由 LoadScheduler 通过 WebSocket 每秒推送，
 * 不插入 DB，图表上也不会有粒度不匹配的问题。</p>
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

    @Scheduled(fixedRate = 30_000)
    public void feed() {
        try {
            LoadData latest = loadDataService.getLatest();
            if (latest == null || latest.getLoadMw() == null) return;

            LocalDateTime lastTime = latest.getTime();
            LocalDateTime nextHour = lastTime.plusHours(1).truncatedTo(ChronoUnit.HOURS);
            LocalDateTime now = LocalDateTime.now();

            // 还没到下一个整点，不插入
            if (!now.isAfter(nextHour)) return;

            float pattern = (float) HOURLY_PATTERN[nextHour.getHour()];
            float prevLoad = latest.getLoadMw();
            // 继承 80% 上一值 + 20% 目标值 + 噪声
            float loadMw = (float) (prevLoad * 0.8 + pattern * 1000 * 0.2 + random.nextGaussian() * 10);
            loadMw = Math.max(50, loadMw);

            float prevTemp = latest.getTemperature() != null ? latest.getTemperature() : 20;
            float prevHum = latest.getHumidity() != null ? latest.getHumidity() : 60;

            LoadData row = new LoadData();
            row.setTime(nextHour);
            row.setLoadMw(loadMw);
            row.setTemperature(prevTemp + (float) random.nextGaussian() * 0.3f);
            row.setHumidity((float) Math.max(0, Math.min(100, prevHum + random.nextGaussian() * 1.5)));
            row.setHour(nextHour.getHour());
            row.setDayOfWeek(nextHour.getDayOfWeek().getValue() % 7);
            row.setMonth(nextHour.getMonthValue());
            row.setIsHoliday(0);
            row.setCreatedAt(LocalDateTime.now());

            loadDataMapper.insert(row);
            pushService.pushLoad(row);
            log.info("Hourly data inserted: {} → {}MW", nextHour, loadMw);
        } catch (Exception e) {
            if (!e.getMessage().contains("Duplicate")) {
                log.error("Feed error: {}", e.getMessage());
            }
        }
    }
}
