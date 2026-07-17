package com.powerload.scheduler;

import com.powerload.entity.LoadData;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.service.LoadDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 模拟小时级数据源 — 每 15 秒检查并补齐缺口。
 *
 * <p>使用 MockLoadProfile 计算期望负荷，通过历史偏移衰减实现平滑衔接。
 * 偏移约 4–8 小时内衰减至零，长时间补齐后回归正常日负荷曲线。
 * 数据来源标记为 RECOVERED_SIMULATION。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockDataFeeder {

    private final LoadDataMapper loadDataMapper;
    private final LoadDataService loadDataService;

    /** 偏移衰减半衰期（小时） */
    static final int DECAY_HALF_LIFE_HOURS = 3;

    /** 单小时最大变化 (MW) — 只拦截异常跳变，不削平正常峰谷 */
    static final double MAX_HOURLY_DELTA = 120.0;

    @Scheduled(fixedRate = 15_000)
    public void feed() {
        try {
            LoadData latest = loadDataService.getLatestHourly();
            if (latest == null || latest.getLoadMw() == null) return;

            LocalDateTime cursor = latest.getTime()
                    .withMinute(0).withSecond(0).withNano(0)
                    .plusHours(1);

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);

            // 历史偏移 = 最后一个实际值 - 该时刻的 profile 期望值
            double initialOffset = latest.getLoadMw() - MockLoadProfile.baseLoad(latest.getTime())
                    - DeterministicNoise.loadNoise(latest.getTime());
            long lastTimeEpoch = latest.getTime().atZone(java.time.ZoneId.of("Asia/Shanghai"))
                    .toEpochSecond();

            while (!cursor.isAfter(now)) {
                LocalDateTime target = cursor;

                // 期望负荷
                double profile = MockLoadProfile.baseLoad(target);
                double noise = DeterministicNoise.loadNoise(target);

                // 偏移衰减
                long targetEpoch = target.atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond();
                double hoursSinceLast = (targetEpoch - lastTimeEpoch) / 3600.0;
                double decay = Math.pow(0.5, hoursSinceLast / DECAY_HALF_LIFE_HOURS);
                double offset = initialOffset * decay;

                double loadMw = profile + offset + noise;

                // 单小时变化保护（不削平正常峰谷）
                double prevLoad = latest.getLoadMw();
                if (Math.abs(loadMw - prevLoad) > MAX_HOURLY_DELTA) {
                    loadMw = prevLoad + Math.copySign(MAX_HOURLY_DELTA, loadMw - prevLoad);
                }
                loadMw = Math.max(50, loadMw);

                // 温度
                double tempProfile = MockLoadProfile.temperature(target);
                double tempNoise = DeterministicNoise.tempNoise(target);
                double temperature = tempProfile + tempNoise;

                // 湿度
                double humProfile = MockLoadProfile.humidity(target);
                double humNoise = DeterministicNoise.humNoise(target);
                double humidity = Math.max(0, Math.min(100, humProfile + humNoise));

                LoadData row = new LoadData();
                row.setTime(target);
                row.setLoadMw((float) loadMw);
                row.setTemperature((float) temperature);
                row.setHumidity((float) humidity);
                row.setHour(MockLoadProfile.hour(target));
                row.setDayOfWeek(MockLoadProfile.dayOfWeek(target));
                row.setMonth(MockLoadProfile.month(target));
                row.setIsHoliday(MockLoadProfile.isHolidayInt(target));
                row.setDataSource("RECOVERED_SIMULATION");
                row.setCreatedAt(LocalDateTime.now());

                try {
                    loadDataMapper.insert(row);
                } catch (DuplicateKeyException e) {
                    log.debug("整点 {} 已存在，跳过", target);
                }

                cursor = cursor.plusHours(1);
                latest = row;
            }
        } catch (Exception e) {
            log.error("Feed error: {}", e.getMessage());
        }
    }
}
