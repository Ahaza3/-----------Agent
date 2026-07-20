package com.powerload.scheduler;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * 确定性噪声生成器 — 同一时间点重复生成结果一致。
 *
 * <p>种子 = FIXED_SEED + timeHash + type，保证不同数据类型噪声不相关。</p>
 */
public final class DeterministicNoise {

    /** 项目固定种子 */
    private static final long FIXED_SEED = 0x4C6F616453696D01L; // "LoadSim"+1

    /** 噪声标准差 (MW) — 与 Python NOISE_SIGMA=30 一致 */
    public static final double LOAD_NOISE_SIGMA = 30.0;

    /** 温度噪声标准差 */
    public static final double TEMP_NOISE_SIGMA = 3.0;

    /** 湿度噪声标准差 */
    public static final double HUM_NOISE_SIGMA = 8.0;

    private DeterministicNoise() {}

    /** 为目标时间生成负荷噪声 (MW) */
    public static double loadNoise(LocalDateTime time) {
        return gaussian(time, 0);
    }

    /** 为目标时间生成温度噪声 (°C) */
    public static double tempNoise(LocalDateTime time) {
        return gaussian(time, 1);
    }

    /** 为目标时间生成湿度噪声 (%) */
    public static double humNoise(LocalDateTime time) {
        return gaussian(time, 2);
    }

    private static double gaussian(LocalDateTime time, int type) {
        long seed = FIXED_SEED
                ^ ((long) time.getYear() << 40)
                ^ ((long) time.getMonthValue() << 36)
                ^ ((long) time.getDayOfMonth() << 28)
                ^ ((long) time.getHour() << 20)
                ^ ((long) type << 16);
        Random rng = new Random(seed);
        // Box-Muller 用一个 Random 实例产生一次结果即可
        double u1 = rng.nextDouble();
        double u2 = rng.nextDouble();
        // 如果 u1 接近 0，用 safeguard
        if (u1 < 1e-10) u1 = 1e-10;
        double z = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
        double sigma = switch (type) {
            case 0 -> LOAD_NOISE_SIGMA;
            case 1 -> TEMP_NOISE_SIGMA;
            case 2 -> HUM_NOISE_SIGMA;
            default -> 1.0;
        };
        return z * sigma;
    }

    /** 判断两个时间的噪声是否不同（测试用） */
    public static boolean noiseDiffers(LocalDateTime a, LocalDateTime b) {
        return Math.abs(loadNoise(a) - loadNoise(b)) > 1e-9;
    }
}
