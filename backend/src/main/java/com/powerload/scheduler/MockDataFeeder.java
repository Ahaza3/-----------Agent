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
 * 模拟实时数据源 — 弥补 mock 数据与当前时间之间的缺口，持续产出数据
 *
 * <p>启动时自动填充历史数据缺口（最新记录 → 当前整点），
 * 之后每秒基于最新值 + 随机波动 INSERT 新数据行。</p>
 *
 * <p>数据连续性保证：
 * <ol>
 *   <li>不管 mock 数据截止到哪天，启动时自动补齐到当前小时</li>
 *   <li>补齐用线性插值 + 小噪声，视觉平滑</li>
 *   <li>之后每秒产出一条新数据</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockDataFeeder {

    private final LoadDataMapper loadDataMapper;
    private final LoadDataService loadDataService;

    private final Random random = new Random();

    /**
     * 启动时补齐历史缺口，使时间序列连续到当前时刻
     */
    @PostConstruct
    public void fillGap() {
        LoadData latest = loadDataService.getLatest();
        if (latest == null || latest.getTime() == null) {
            log.info("数据库无历史数据，跳过补齐");
            return;
        }

        LocalDateTime lastTime = latest.getTime();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        long gapHours = ChronoUnit.HOURS.between(lastTime, now);

        if (gapHours <= 0) {
            log.info("数据已是最新 (last={})，无需补齐", lastTime);
            return;
        }

        log.info("检测到时间缺口: {} → {} ({} 小时)，开始补齐...", lastTime, now, gapHours);

        LoadData previous = latest;
        int filled = 0;
        for (int h = 1; h <= gapHours; h++) {
            LocalDateTime t = lastTime.plusHours(h);
            LoadData row = buildRow(t, previous);
            loadDataMapper.insert(row);
            previous = row;
            filled++;
        }

        log.info("缺口补齐完成: {} 条数据, 最新时间 = {}", filled,
                lastTime.plusHours(gapHours));
    }

    /**
     * 每秒产出一条模拟实时数据
     */
    @Scheduled(fixedRate = 1000)
    public void feed() {
        try {
            LoadData latest = loadDataService.getLatest();
            if (latest == null) return;

            LocalDateTime now = LocalDateTime.now();
            // 跳过已存在的同一秒记录
            if (latest.getTime() != null
                    && ChronoUnit.SECONDS.between(latest.getTime(), now) < 1) {
                return;
            }

            LoadData row = buildRow(now, latest);
            loadDataMapper.insert(row);
        } catch (Exception e) {
            // 吞掉重复键异常（并发启动可能同时 insert）
            if (!e.getMessage().contains("Duplicate")) {
                log.error("实时数据产出异常", e);
            }
        }
    }

    /**
     * 基于前一条记录构造新数据行（小波动模拟真实负荷变化）
     */
    private LoadData buildRow(LocalDateTime time, LoadData previous) {
        LoadData row = new LoadData();
        row.setTime(time);
        row.setHour(time.getHour());
        row.setDayOfWeek(time.getDayOfWeek().getValue() % 7);
        row.setMonth(time.getMonthValue());
        row.setIsHoliday(0);

        // 负荷：基准值 × 小时系数 + 随机波动
        double baseLoad = previous.getLoadMw() != null ? previous.getLoadMw() : 800;
        double hourFactor = getHourlyFactor(time.getHour());
        double loadMw = baseLoad * 0.7 + hourFactor * 1000 * 0.3 + random.nextGaussian() * 8;
        row.setLoadMw((float) Math.max(0, loadMw));

        // 温度：微调
        double prevTemp = previous.getTemperature() != null ? previous.getTemperature() : 20;
        row.setTemperature((float) (prevTemp + random.nextGaussian() * 0.3));

        // 湿度：微调
        double prevHum = previous.getHumidity() != null ? previous.getHumidity() : 60;
        double hum = prevHum + random.nextGaussian() * 1.5;
        row.setHumidity((float) Math.max(0, Math.min(100, hum)));

        row.setCreatedAt(LocalDateTime.now());
        return row;
    }

    /** 典型日负荷双峰系数 */
    private double getHourlyFactor(int hour) {
        double[] pattern = {
            0.65, 0.60, 0.58, 0.55, 0.52, 0.55, 0.62, 0.75,
            0.88, 0.95, 1.00, 0.93, 0.85, 0.82, 0.88, 0.92,
            0.98, 1.00, 0.96, 0.90, 0.85, 0.80, 0.75, 0.70,
        };
        return pattern[hour % 24];
    }
}
