package com.powerload.scheduler;

import com.powerload.entity.LoadData;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.service.LoadDataService;
import jakarta.annotation.PostConstruct;
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
 * <p>启动时补齐缺口（旧数据最后时刻 → 当前整点），之后每秒产出一条平滑微调数据。</p>
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

    private volatile LocalDateTime lastFeedTime = null;

    @PostConstruct
    public void fillGap() {
        LoadData latest = loadDataService.getLatest();
        if (latest == null || latest.getTime() == null) {
            log.info("No historical data, skip gap filling");
            return;
        }

        LocalDateTime from = latest.getTime();
        LocalDateTime to = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        long gapHours = ChronoUnit.HOURS.between(from, to);

        if (gapHours <= 0) {
            log.info("Data already up to date (latest={})", from);
            lastFeedTime = from;
            return;
        }

        log.info("Filling gap: {} → {} ({} hours)", from, to, gapHours);
        float lastLoad = latest.getLoadMw() != null ? latest.getLoadMw() : 800f;

        for (int h = 1; h <= gapHours; h++) {
            LocalDateTime t = from.plusHours(h);
            double pattern = HOURLY_PATTERN[t.getHour()];
            // 平滑过渡：前 24 小时从旧值渐变，之后跟随时间模式
            float smooth;
            if (h <= 24) {
                float ratio = (float) h / 24f;
                smooth = lastLoad * (1 - ratio) + (float) (pattern * 1000) * ratio;
            } else {
                smooth = (float) (pattern * 1000);
            }
            float noise = (float) random.nextGaussian() * 10;
            float val = Math.max(0, smooth + noise);

            LoadData row = new LoadData();
            row.setTime(t);
            row.setLoadMw(val);
            row.setTemperature(15f + (float) random.nextGaussian());
            row.setHumidity(60f + (float) random.nextGaussian() * 2);
            row.setHour(t.getHour());
            row.setDayOfWeek(t.getDayOfWeek().getValue() % 7);
            row.setMonth(t.getMonthValue());
            row.setIsHoliday(0);
            row.setCreatedAt(LocalDateTime.now());
            loadDataMapper.insert(row);
        }

        lastFeedTime = to;
        log.info("Gap filled: {} rows", gapHours);
    }

    @Scheduled(fixedRate = 1000)
    public void feed() {
        try {
            LoadData latest = loadDataService.getLatest();
            if (latest == null || latest.getLoadMw() == null) return;

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

            // 同一秒不重复插入
            if (lastFeedTime != null && !now.isAfter(lastFeedTime)) return;
            lastFeedTime = now;

            // 负荷：基于上一秒微调 ±3MW
            float prevLoad = latest.getLoadMw();
            float delta = (float) random.nextGaussian() * 3f;
            float newLoad = Math.max(0, prevLoad + delta);

            // 温度/湿度微调
            float prevTemp = latest.getTemperature() != null ? latest.getTemperature() : 20;
            float prevHum = latest.getHumidity() != null ? latest.getHumidity() : 60;

            LoadData row = new LoadData();
            row.setTime(now);
            row.setLoadMw(newLoad);
            row.setTemperature(prevTemp + (float) random.nextGaussian() * 0.1f);
            row.setHumidity((float) Math.max(0, Math.min(100, prevHum + random.nextGaussian() * 0.5)));
            row.setHour(now.getHour());
            row.setDayOfWeek(now.getDayOfWeek().getValue() % 7);
            row.setMonth(now.getMonthValue());
            row.setIsHoliday(0);
            row.setCreatedAt(LocalDateTime.now());

            loadDataMapper.insert(row);
        } catch (Exception e) {
            if (!e.getMessage().contains("Duplicate")) {
                log.error("Feed error", e);
            }
        }
    }
}
