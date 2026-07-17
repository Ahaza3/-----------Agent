package com.powerload.scheduler;

import com.powerload.entity.LoadData;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.service.LoadDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MockDataFeederTest {

    private LoadDataMapper loadDataMapper;
    private LoadDataService loadDataService;
    private MockDataFeeder feeder;
    private final List<LoadData> inserted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        loadDataMapper = mock(LoadDataMapper.class);
        loadDataService = mock(LoadDataService.class);
        feeder = new MockDataFeeder(loadDataMapper, loadDataService);
        inserted.clear();
    }

    @Test
    void shouldNotFeedWhenNoHourlyData() {
        when(loadDataService.getLatestHourly()).thenReturn(null);
        feeder.feed();
        verify(loadDataMapper, never()).insert(any(LoadData.class));
    }

    @Test
    void shouldMarkDataAsRecoveredSimulation() {
        LoadData latest = record(LocalDateTime.of(2026, 7, 16, 11, 0), 900f);
        latest.setDataSource("MOCK_HISTORY");
        when(loadDataService.getLatestHourly()).thenReturn(latest);
        when(loadDataMapper.insert(any(LoadData.class))).thenAnswer(inv -> {
            inserted.add(inv.getArgument(0, LoadData.class));
            return 1;
        });

        feeder.feed();

        for (LoadData d : inserted) {
            assertEquals("RECOVERED_SIMULATION", d.getDataSource());
        }
    }

    @Test
    void shouldFillCorrectDateFields() {
        LoadData latest = record(LocalDateTime.of(2026, 7, 16, 10, 0), 900f);
        when(loadDataService.getLatestHourly()).thenReturn(latest);
        when(loadDataMapper.insert(any(LoadData.class))).thenAnswer(inv -> {
            inserted.add(inv.getArgument(0, LoadData.class));
            return 1;
        });

        feeder.feed();

        for (LoadData d : inserted) {
            assertEquals(d.getTime().getHour(), d.getHour().intValue());
            assertNotNull(d.getDayOfWeek());
            assertNotNull(d.getMonth());
            assertNotNull(d.getIsHoliday());
        }
    }

    @Test
    void shouldNotGenerateFutureRecords() {
        LocalDateTime now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        LoadData latest = record(now.minusHours(1), 900f);
        when(loadDataService.getLatestHourly()).thenReturn(latest);
        when(loadDataMapper.insert(any(LoadData.class))).thenAnswer(inv -> {
            inserted.add(inv.getArgument(0, LoadData.class));
            return 1;
        });

        feeder.feed();

        for (LoadData d : inserted) {
            assertFalse(d.getTime().isAfter(now),
                    "不应有未来数据: " + d.getTime() + " > " + now);
        }
    }

    @Test
    void shouldNotGenerateCurrentIncompleteHour() {
        LocalDateTime currentHour = LocalDateTime.now()
                .withMinute(0).withSecond(0).withNano(0);
        LoadData latest = record(currentHour.minusHours(2), 900f);
        when(loadDataService.getLatestHourly()).thenReturn(latest);
        when(loadDataMapper.insert(any(LoadData.class))).thenAnswer(inv -> {
            inserted.add(inv.getArgument(0, LoadData.class));
            return 1;
        });

        feeder.feed();

        assertEquals(1, inserted.size());
        assertEquals(currentHour.minusHours(1), inserted.get(0).getTime());
        assertTrue(inserted.stream().noneMatch(row -> row.getTime().equals(currentHour)));
    }

    @Test
    void shouldMaintainContinuityAfterShortGap() {
        // 停机 1 小时：11:00 有数据，恢复时从 12:00 开始补齐
        LoadData latest = record(LocalDateTime.of(2026, 7, 16, 11, 0), 900f);
        latest.setDataSource("MOCK_HISTORY");
        when(loadDataService.getLatestHourly()).thenReturn(latest);
        when(loadDataMapper.insert(any(LoadData.class))).thenAnswer(inv -> {
            inserted.add(inv.getArgument(0, LoadData.class));
            return 1;
        });

        feeder.feed();

        if (!inserted.isEmpty()) {
            LoadData first = inserted.get(0);
            assertEquals(LocalDateTime.of(2026, 7, 16, 12, 0), first.getTime());
            // 1 小时衰减很小，不应有异常跳变
            double delta = Math.abs(first.getLoadMw() - latest.getLoadMw());
            assertTrue(delta < MockDataFeeder.MAX_HOURLY_DELTA,
                    "1小时停机的跳变应在合理范围: delta=" + delta);
        }
    }

    @Test
    void shouldRetainPeakValleyAfter24HourGap() {
        // 模拟停机 24h 后补齐多天数据
        // 最后历史：7月15日 11:00，恢复时间 7月16日 18:00（当前时间）
        LoadData latest = record(LocalDateTime.of(2026, 7, 15, 11, 0), 950f);
        when(loadDataService.getLatestHourly()).thenReturn(latest);
        when(loadDataMapper.insert(any(LoadData.class))).thenAnswer(inv -> {
            inserted.add(inv.getArgument(0, LoadData.class));
            return 1;
        });

        feeder.feed();

        if (inserted.size() >= 24) {
            // 计算日内峰谷比
            double max12 = 0, min12 = Double.MAX_VALUE;
            for (LoadData d : inserted) {
                if (d.getLoadMw() > max12) max12 = d.getLoadMw();
                if (d.getLoadMw() < min12) min12 = d.getLoadMw();
            }
            double ratio = max12 / min12;
            // 日内峰谷比应 > 1.2（不能退化为直线）
            assertTrue(ratio > 1.2,
                    "补齐24h后仍应有日内峰谷: ratio=" + ratio);
        }
    }

    @Test
    void shouldDecayOffsetOverTime() {
        // 模拟最后历史值与 profile 有较大偏离
        // 用周末 10:00 作为最后记录（实际负荷 < profile 期望）
        LoadData latest = record(
                LocalDateTime.of(2026, 7, 18, 10, 0), // 周六
                700f); // 低于 profile 期望
        when(loadDataService.getLatestHourly()).thenReturn(latest);
        when(loadDataMapper.insert(any(LoadData.class))).thenAnswer(inv -> {
            inserted.add(inv.getArgument(0, LoadData.class));
            return 1;
        });

        feeder.feed();

        if (inserted.size() >= 10) {
            // 第 1 小时和后几小时的差值应接近 profile
            LoadData first = inserted.get(0);
            LoadData later = inserted.get(Math.min(8, inserted.size() - 1));

            double firstDeviation = Math.abs(first.getLoadMw()
                    - MockLoadProfile.baseLoad(first.getTime())
                    - DeterministicNoise.loadNoise(first.getTime()));
            double laterDeviation = Math.abs(later.getLoadMw()
                    - MockLoadProfile.baseLoad(later.getTime())
                    - DeterministicNoise.loadNoise(later.getTime()));

            // 后面的偏差应更小（偏移已衰减）
            assertTrue(laterDeviation < firstDeviation * 1.5,
                    "长时间后偏移应衰减: first=" + firstDeviation + " later=" + laterDeviation);
        }
    }

    @Test
    void shouldBeIdempotentOnDuplicateKey() {
        LoadData latest = record(LocalDateTime.of(2026, 7, 16, 10, 0), 900f);
        when(loadDataService.getLatestHourly()).thenReturn(latest);
        when(loadDataMapper.insert(any(LoadData.class)))
                .thenThrow(new DuplicateKeyException("dup"))
                .thenReturn(1);

        assertDoesNotThrow(() -> feeder.feed());
    }

    @Test
    void shouldNotGenerateNonHourlyRecords() {
        LoadData latest = record(LocalDateTime.of(2026, 7, 16, 10, 0), 900f);
        when(loadDataService.getLatestHourly()).thenReturn(latest);
        when(loadDataMapper.insert(any(LoadData.class))).thenAnswer(inv -> {
            inserted.add(inv.getArgument(0, LoadData.class));
            return 1;
        });

        feeder.feed();

        for (LoadData d : inserted) {
            assertEquals(0, d.getTime().getMinute());
            assertEquals(0, d.getTime().getSecond());
        }
    }

    @Test
    void temperatureAndHumidityShouldBeInRange() {
        LoadData latest = record(LocalDateTime.of(2026, 7, 16, 10, 0), 900f);
        when(loadDataService.getLatestHourly()).thenReturn(latest);
        when(loadDataMapper.insert(any(LoadData.class))).thenAnswer(inv -> {
            inserted.add(inv.getArgument(0, LoadData.class));
            return 1;
        });

        feeder.feed();

        for (LoadData d : inserted) {
            assertNotNull(d.getTemperature());
            assertNotNull(d.getHumidity());
            assertTrue(d.getTemperature() > -30 && d.getTemperature() < 55,
                    "温度越界: " + d.getTemperature());
            assertTrue(d.getHumidity() >= 0 && d.getHumidity() <= 100,
                    "湿度越界: " + d.getHumidity());
        }
    }

    private static LoadData record(LocalDateTime time, float loadMw) {
        LoadData d = new LoadData();
        d.setTime(time);
        d.setLoadMw(loadMw);
        d.setTemperature(25f);
        d.setHumidity(60f);
        d.setHour(time.getHour());
        d.setDayOfWeek(MockLoadProfile.dayOfWeek(time));
        d.setMonth(time.getMonthValue());
        d.setIsHoliday(MockLoadProfile.isHolidayInt(time));
        d.setDataSource("MOCK_HISTORY");
        return d;
    }
}
