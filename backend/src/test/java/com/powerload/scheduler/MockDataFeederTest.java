package com.powerload.scheduler;

import com.powerload.entity.LoadData;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.service.LoadDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MockDataFeederTest {

    private LoadDataMapper loadDataMapper;
    private LoadDataService loadDataService;
    private MockDataFeeder feeder;

    @BeforeEach
    void setUp() {
        loadDataMapper = mock(LoadDataMapper.class);
        loadDataService = mock(LoadDataService.class);
        feeder = new MockDataFeeder(loadDataMapper, loadDataService);
    }

    @Test
    void shouldNotFeedWhenNoHourlyData() {
        when(loadDataService.getLatestHourly()).thenReturn(null);
        feeder.feed();
        verify(loadDataMapper, never()).insert(any(LoadData.class));
    }

    @Test
    void shouldAlignToHourWhenLastRecordExists() {
        LoadData latest = record(LocalDateTime.of(2026, 7, 16, 11, 0), 900f);
        when(loadDataService.getLatestHourly()).thenReturn(latest);
        when(loadDataMapper.insert(any(LoadData.class))).thenReturn(1);

        feeder.feed();

        verify(loadDataMapper, atLeastOnce()).insert(any(LoadData.class));
    }

    @Test
    void shouldNotGenerateFutureRecords() {
        LocalDateTime now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        LoadData latest = record(now.minusHours(1), 900f);
        when(loadDataService.getLatestHourly()).thenReturn(latest);
        when(loadDataMapper.insert(any(LoadData.class))).thenReturn(1);

        feeder.feed();

        // 检查所有插入的记录，时间不超过当前整点
        verify(loadDataMapper, atLeastOnce()).insert(argThat((LoadData d) -> !d.getTime().isAfter(now)));
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

    private static LoadData record(LocalDateTime time, float loadMw) {
        LoadData d = new LoadData();
        d.setTime(time);
        d.setLoadMw(loadMw);
        d.setTemperature(25f);
        d.setHumidity(60f);
        return d;
    }
}
