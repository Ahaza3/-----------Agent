package com.powerload.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.ChronoField;

/**
 * 统一模拟负荷特征模型 — 与 ml/generate_mock_data.py 保持一致。
 *
 * <p>根据目标时间计算基础负荷期望值、温度、湿度、日期标记。
 * 不含状态、不含噪声— 纯函数，线程安全，测试友好。</p>
 */
public final class MockLoadProfile {

    private MockLoadProfile() {}

    /* ─── 核心参数（与 Python 一致） ─── */

    public static final double BASE_LOAD = 1000.0;       // MW
    public static final double WEEKEND_FACTOR = 0.85;
    public static final double HOLIDAY_EXTRA = 0.90;     // 节假日叠加折扣
    public static final double SEASONAL_AMPLITUDE = 0.15;

    /** 24 小时负荷系数（双峰：10:00 早高峰 + 18:00 晚高峰） */
    public static final double[] HOURLY_COEFF = {
        0.65, 0.60, 0.58, 0.55,   // 00–03 深夜低谷
        0.52, 0.55, 0.62, 0.75,   // 04–07 凌晨谷底→上升
        0.88, 0.95, 1.00, 0.93,   // 08–11 早高峰
        0.85, 0.82, 0.88, 0.92,   // 12–15 午后
        0.98, 1.00, 0.96, 0.90,   // 16–19 晚高峰
        0.85, 0.80, 0.75, 0.70,   // 20–23 夜间
    };

    /* ─── 公开方法 ─── */

    /** 基础负荷期望值（含小时/星期/节假日/季节系数，不含噪声） */
    public static double baseLoad(LocalDateTime time) {
        double base = BASE_LOAD * HOURLY_COEFF[time.getHour()];

        if (isHoliday(time.toLocalDate())) {
            base *= WEEKEND_FACTOR * HOLIDAY_EXTRA;
        } else if (isWeekend(time)) {
            base *= WEEKEND_FACTOR;
        }

        base *= seasonalFactor(time.getDayOfYear());
        return base;
    }

    /** 计算指定时间的温度 (°C)，含季节 + 昼夜变化 */
    public static double temperature(LocalDateTime time) {
        int doy = time.getDayOfYear();
        double seasonal = 15.0 + 12.0 * Math.sin(2 * Math.PI * (doy - 105) / 365.0);
        int h = time.getHour();
        double diurnal = (h >= 8 && h <= 17) ? 5.0 : 0.0;
        return seasonal + diurnal;
    }

    /** 计算指定时间的湿度 (%)，仅含季节变化 */
    public static double humidity(LocalDateTime time) {
        int doy = time.getDayOfYear();
        return 50.0 + 20.0 * Math.sin(2 * Math.PI * (doy + 60) / 365.0);
    }

    /** 日期是否为节假日（元旦 1/1-3、五一 5/1-5、国庆 10/1-7） */
    public static boolean isHoliday(LocalDate date) {
        int m = date.getMonthValue();
        int d = date.getDayOfMonth();
        return (m == 1 && d <= 3) || (m == 5 && d <= 5) || (m == 10 && d <= 7);
    }

    /** 日期是否为周末 */
    public static boolean isWeekend(LocalDateTime time) {
        int dow = time.getDayOfWeek().getValue(); // 1=Mon … 7=Sun
        return dow >= 6;
    }

    /** 季节系数：夏季高、冬季低 */
    public static double seasonalFactor(int dayOfYear) {
        return 1.0 + SEASONAL_AMPLITUDE * Math.sin(2 * Math.PI * (dayOfYear - 180) / 365.0);
    }

    /* ─── 日期字段 ─── */

    /** 0=周一 … 6=周日（与数据库、Python 一致） */
    public static int dayOfWeek(LocalDateTime time) {
        return time.getDayOfWeek().getValue() - 1;
    }

    public static int month(LocalDateTime time) {
        return time.getMonthValue();
    }

    public static int hour(LocalDateTime time) {
        return time.getHour();
    }

    public static int isHolidayInt(LocalDateTime time) {
        return isHoliday(time.toLocalDate()) ? 1 : 0;
    }

    /* ─── 夏季/冬季辅助 ─── */

    public static boolean isSummer(int month) {
        return month >= 6 && month <= 8;
    }

    public static boolean isWinter(int month) {
        return month == 12 || month <= 2;
    }
}
