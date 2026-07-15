package com.powerload.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 时区转换一致性测试 — 验证带时区的 ISO 时间正确转换到 Asia/Shanghai
 *
 * <p>不依赖 Spring 上下文，纯单元测试时区转换逻辑。</p>
 */
class LoadDataControllerTimezoneTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    @DisplayName("7-1. UTC 'Z' 结尾时间正确转换为 Asia/Shanghai（+8h）")
    void testUtcZ_convertsToShanghai() {
        // 2024-07-15 00:00:00 UTC → Asia/Shanghai = 2024-07-15 08:00:00
        Instant instant = Instant.parse("2024-07-15T00:00:00Z");
        LocalDateTime shanghaiTime = LocalDateTime.ofInstant(instant, SHANGHAI);

        assertEquals(2024, shanghaiTime.getYear());
        assertEquals(7, shanghaiTime.getMonthValue());
        assertEquals(15, shanghaiTime.getDayOfMonth());
        assertEquals(8, shanghaiTime.getHour()); // UTC 0h + 8h = Shanghai 8h
        assertEquals(0, shanghaiTime.getMinute());
    }

    @Test
    @DisplayName("7-2. +08:00 offset 时间转换结果与 'Z' 转换一致")
    void testPlus0800_convertsSameAsUtc() {
        // 2024-07-15T00:00:00+08:00 = same instant as 2024-07-14T16:00:00Z
        Instant fromPlus8 = Instant.parse("2024-07-15T00:00:00+08:00");
        Instant fromUtc = Instant.parse("2024-07-14T16:00:00Z");

        assertEquals(fromUtc, fromPlus8, "same instant");

        LocalDateTime shanghaiTime = LocalDateTime.ofInstant(fromPlus8, SHANGHAI);
        assertEquals(2024, shanghaiTime.getYear());
        assertEquals(7, shanghaiTime.getMonthValue());
        assertEquals(15, shanghaiTime.getDayOfMonth());
        assertEquals(0, shanghaiTime.getHour()); // Asia/Shanghai 0h
        assertEquals(0, shanghaiTime.getMinute());
    }

    @Test
    @DisplayName("7-3. 同一 UTC 时刻的 +08:00 和 Z 查询范围一致")
    void testSameUtcMoment_sameQueryWindow() {
        // 前端在 Asia/Shanghai 发送"近24小时"
        // start = 2024-07-14T08:00:00.000Z (= Shanghai 2024-07-14 16:00)
        // end   = 2024-07-15T08:00:00.000Z (= Shanghai 2024-07-15 16:00)
        Instant startZ = Instant.parse("2024-07-14T08:00:00Z");
        Instant endZ = Instant.parse("2024-07-15T08:00:00Z");

        // 如果前端用 +08:00 格式
        Instant startP8 = Instant.parse("2024-07-14T16:00:00+08:00");
        Instant endP8 = Instant.parse("2024-07-15T16:00:00+08:00");

        // 应该是同一个 instant
        assertEquals(startZ, startP8);
        assertEquals(endZ, endP8);

        // 转换到数据库查询 LocalDateTime
        LocalDateTime startDb = LocalDateTime.ofInstant(startZ, SHANGHAI);
        LocalDateTime endDb = LocalDateTime.ofInstant(endZ, SHANGHAI);

        assertEquals("2024-07-14T16:00", startDb.toString());
        assertEquals("2024-07-15T16:00", endDb.toString());
    }

    @Test
    @DisplayName("7-4. 无时区 LocalDateTime 在解析时可能偏移 — 验证 Instant 接收避免问题")
    void testInstantAvoidsSilentTimezoneDrop() {
        // 如果直接接收 LocalDateTime，Spring 可能静默丢弃时区信息
        // 使用 Instant 接收后显式转换，任何 offset 都能正确映射
        Instant withOffset = Instant.parse("2024-07-15T08:00:00+05:30"); // IST
        LocalDateTime shanghai = LocalDateTime.ofInstant(withOffset, SHANGHAI);

        // IST +5:30 = UTC 02:30 → Shanghai +8 = 10:30
        assertEquals(10, shanghai.getHour());
        assertEquals(30, shanghai.getMinute());
    }
}
