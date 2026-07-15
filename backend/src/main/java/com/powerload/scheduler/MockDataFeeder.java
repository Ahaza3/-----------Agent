package com.powerload.scheduler;

import com.powerload.entity.LoadData;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.service.LoadDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;

/**
 * 模拟小时级数据源 — 每 15 秒检查并补齐缺口，使 DB 始终有到当前整点的数据
 *
 * <p>注意：此组件仅负责补齐小时级 DB 数据，不推送实时 WebSocket 消息。
 * 实时推送由 {@link LoadScheduler} 通过 {@link com.powerload.service.RealtimeLoadService} 完成。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockDataFeeder {

    private final LoadDataMapper loadDataMapper;
    private final LoadDataService loadDataService;
    private final Random random = new Random();

    private static final double[] HOURLY_PATTERN = {
        0.65, 0.60, 0.58, 0.55, 0.52, 0.55, 0.62, 0.75,
        0.88, 0.95, 1.00, 0.93, 0.85, 0.82, 0.88, 0.92,
        0.98, 1.00, 0.96, 0.90, 0.85, 0.80, 0.75, 0.70,
    };

    @Scheduled(fixedRate = 15_000)
    public void feed() {
        try {
            LoadData latest = loadDataService.getLatest();
            if (latest == null || latest.getLoadMw() == null) return;

            LocalDateTime cursor = latest.getTime();
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);

            while (cursor.isBefore(now)) {
                LocalDateTime nextHour = cursor.plusHours(1);

                float pattern = (float) HOURLY_PATTERN[nextHour.getHour()];
                float prevLoad = latest.getLoadMw();

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
                // 不推送 WebSocket — 实时链路由 LoadScheduler + RealtimeLoadService 负责
                cursor = nextHour;
                latest = row;
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("Duplicate")) {
                log.error("Feed error: {}", e.getMessage());
            }
        }
    }
}
