package com.powerload.scheduler;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MockLoadProfileTest {

    /* ─── 工作日 vs 周末 ─── */

    @Test
    void weekendLoadShouldBeLowerThanWeekday() {
        // 周一 10:00
        LocalDateTime mon10 = LocalDateTime.of(2026, 7, 13, 10, 0);
        // 周六 10:00
        LocalDateTime sat10 = LocalDateTime.of(2026, 7, 18, 10, 0);

        double monLoad = MockLoadProfile.baseLoad(mon10);
        double satLoad = MockLoadProfile.baseLoad(sat10);

        assertTrue(satLoad < monLoad,
                "周末负荷应低于工作日: sat=" + satLoad + " mon=" + monLoad);
    }

    @Test
    void weekdayShouldHaveVisiblePeaks() {
        // 凌晨 4:00 应显著低于 10:00
        LocalDateTime t4 = LocalDateTime.of(2026, 7, 15, 4, 0);
        LocalDateTime t10 = LocalDateTime.of(2026, 7, 15, 10, 0);

        double load4 = MockLoadProfile.baseLoad(t4);
        double load10 = MockLoadProfile.baseLoad(t10);

        assertTrue(load10 > load4 * 1.5,
                "早高峰应明显高于凌晨低谷: 10h=" + load10 + " 4h=" + load4);
    }

    /* ─── 节假日 ─── */

    @Test
    void holidayLoadShouldBeEvenLower() {
        // 国庆 10/1 10:00
        LocalDateTime holiday = LocalDateTime.of(2026, 10, 1, 10, 0);
        // 普通周四 10:00 (10/8)
        LocalDateTime weekday = LocalDateTime.of(2026, 10, 8, 10, 0);

        double hLoad = MockLoadProfile.baseLoad(holiday);
        double wLoad = MockLoadProfile.baseLoad(weekday);

        assertTrue(hLoad < wLoad,
                "节假日应低于普通工作日: holiday=" + hLoad + " weekday=" + wLoad);
    }

    @Test
    void mayDayIsHoliday() {
        assertTrue(MockLoadProfile.isHoliday(java.time.LocalDate.of(2026, 5, 1)));
        assertTrue(MockLoadProfile.isHoliday(java.time.LocalDate.of(2026, 5, 5)));
        assertFalse(MockLoadProfile.isHoliday(java.time.LocalDate.of(2026, 5, 6)));
    }

    @Test
    void nationalDayIsHoliday() {
        assertTrue(MockLoadProfile.isHoliday(java.time.LocalDate.of(2026, 10, 1)));
        assertTrue(MockLoadProfile.isHoliday(java.time.LocalDate.of(2026, 10, 7)));
        assertFalse(MockLoadProfile.isHoliday(java.time.LocalDate.of(2026, 10, 8)));
    }

    @Test
    void newYearIsHoliday() {
        assertTrue(MockLoadProfile.isHoliday(java.time.LocalDate.of(2026, 1, 1)));
        assertTrue(MockLoadProfile.isHoliday(java.time.LocalDate.of(2026, 1, 3)));
        assertFalse(MockLoadProfile.isHoliday(java.time.LocalDate.of(2026, 1, 4)));
    }

    /* ─── 季节 ─── */

    @Test
    void summerLoadShouldBeHigherThanWinter() {
        // 夏季 7月
        LocalDateTime summer = LocalDateTime.of(2026, 7, 15, 14, 0);
        // 冬季 1月（需避开元旦假期 1/1-3）
        LocalDateTime winter = LocalDateTime.of(2026, 1, 15, 14, 0);

        double sLoad = MockLoadProfile.baseLoad(summer);
        double wLoad = MockLoadProfile.baseLoad(winter);

        assertTrue(sLoad > wLoad,
                "夏季负荷应高于冬季: summer=" + sLoad + " winter=" + wLoad);
    }

    @Test
    void seasonalFactorShouldVaryCorrectly() {
        // 夏季（7月中旬, doy ≈ 196）
        double summer = MockLoadProfile.seasonalFactor(196);
        // 冬季（1月中旬, doy ≈ 15）
        double winter = MockLoadProfile.seasonalFactor(15);

        assertTrue(summer > 1.0, "夏季系数应 > 1");
        assertTrue(winter < 1.0, "冬季系数应 < 1");
    }

    /* ─── 温度 ─── */

    @Test
    void summerTemperatureShouldBeHigher() {
        double summerTemp = MockLoadProfile.temperature(LocalDateTime.of(2026, 7, 15, 14, 0));
        double winterTemp = MockLoadProfile.temperature(LocalDateTime.of(2026, 1, 15, 14, 0));
        assertTrue(summerTemp > winterTemp,
                "夏季温度应高于冬季: summer=" + summerTemp + " winter=" + winterTemp);
    }

    @Test
    void daytimeTemperatureShouldBeHigher() {
        // 白天 14:00 vs 凌晨 3:00
        double day = MockLoadProfile.temperature(LocalDateTime.of(2026, 7, 15, 14, 0));
        double night = MockLoadProfile.temperature(LocalDateTime.of(2026, 7, 15, 3, 0));
        assertTrue(day > night, "白天应高于夜间: day=" + day + " night=" + night);
    }

    /* ─── 湿度 ─── */

    @Test
    void humidityShouldBeInRange() {
        for (int m = 1; m <= 12; m++) {
            double h = MockLoadProfile.humidity(LocalDateTime.of(2026, m, 15, 12, 0));
            assertTrue(h >= 0 && h <= 100, "湿度越界: m=" + m + " h=" + h);
        }
    }

    /* ─── 日期字段 ─── */

    @Test
    void dayOfWeekShouldMatchDatabaseConvention() {
        // 2026-07-13 is Monday
        assertEquals(0, MockLoadProfile.dayOfWeek(LocalDateTime.of(2026, 7, 13, 0, 0)));
        // 2026-07-19 is Sunday
        assertEquals(6, MockLoadProfile.dayOfWeek(LocalDateTime.of(2026, 7, 19, 0, 0)));
    }

    @Test
    void hourAndMonthShouldBeCorrect() {
        LocalDateTime t = LocalDateTime.of(2026, 12, 25, 23, 0);
        assertEquals(23, MockLoadProfile.hour(t));
        assertEquals(12, MockLoadProfile.month(t));

        // 12/25 not in holiday list → 0
        assertEquals(0, MockLoadProfile.isHolidayInt(LocalDateTime.of(2026, 12, 25, 0, 0)));
        // 10/1 is in holiday list → 1
        assertEquals(1, MockLoadProfile.isHolidayInt(LocalDateTime.of(2026, 10, 1, 0, 0)));
    }
}
