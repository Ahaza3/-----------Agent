package com.powerload.scheduler;

import com.powerload.entity.LoadData;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.service.LoadDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;

/**
 * 模拟小时级数据源 — 每 15 秒检查并补齐缺口，使 DB 始终有到当前整点的数据
 *
 * <p>只使用最后一条合法整点（minute=0, second=0）作为起点，
 * 新记录对齐 HH:00:00，不生成超过当前整点的未来记录。</p>
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
            // 只取合法整点记录作为起点，避免旧版本遗留的秒级数据污染
            LoadData latest = loadDataService.getLatestHourly();
            if (latest == null || latest.getLoadMw() == null) return;

            // 从最后一条整点记录的下一小时开始
            LocalDateTime cursor = latest.getTime()
                    .withMinute(0).withSecond(0).withNano(0)
                    .plusHours(1);

            // 最大补齐到当前整点（不生成未来数据）
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);

            while (!cursor.isAfter(now)) {
                LocalDateTime targetHour = cursor;

                float pattern = (float) HOURLY_PATTERN[targetHour.getHour()];
                float prevLoad = latest.getLoadMw();

                float loadMw = (float) (prevLoad * 0.8 + pattern * 1000 * 0.2 + random.nextGaussian() * 10);
                loadMw = Math.max(50, loadMw);

                float prevTemp = latest.getTemperature() != null ? latest.getTemperature() : 20;
                float prevHum = latest.getHumidity() != null ? latest.getHumidity() : 60;

                LoadData row = new LoadData();
                row.setTime(targetHour); // 对齐 HH:00:00
                row.setLoadMw(loadMw);
                row.setTemperature(prevTemp + (float) random.nextGaussian() * 0.3f);
                row.setHumidity((float) Math.max(0, Math.min(100, prevHum + random.nextGaussian() * 1.5)));
                row.setHour(targetHour.getHour());
                row.setDayOfWeek(targetHour.getDayOfWeek().getValue() % 7);
                row.setMonth(targetHour.getMonthValue());
                row.setIsHoliday(0);
                row.setCreatedAt(LocalDateTime.now());

                try {
                    loadDataMapper.insert(row);
                } catch (DuplicateKeyException e) {
                    // 幂等：该整点已存在则跳过
                    log.debug("整点 {} 已存在，跳过", targetHour);
                }

                cursor = cursor.plusHours(1);
                latest = row;
            }
        } catch (Exception e) {
            log.error("Feed error: {}", e.getMessage());
        }
    }
}
