package com.powerload.service.impl;

import com.powerload.entity.LoadData;
import com.powerload.mapper.LoadDataMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoadDataServiceImplTest {

    @Test
    void queryRangeReturnsOnlyHourlyHistoryPoints() {
        LoadDataMapper mapper = mock(LoadDataMapper.class);
        LoadDataServiceImpl service = new LoadDataServiceImpl(mapper);
        LoadData hourly = loadAt(LocalDateTime.of(2026, 7, 15, 11, 0));
        LoadData legacyRealtime = loadAt(LocalDateTime.of(2026, 7, 15, 11, 43, 12));
        when(mapper.selectList(any())).thenReturn(List.of(hourly, legacyRealtime));

        List<LoadData> result = service.queryRange(
                LocalDateTime.of(2026, 7, 15, 10, 0),
                LocalDateTime.of(2026, 7, 15, 12, 0));

        assertEquals(List.of(hourly), result);
    }

    private static LoadData loadAt(LocalDateTime time) {
        LoadData data = new LoadData();
        data.setTime(time);
        data.setLoadMw(900f);
        return data;
    }
}
