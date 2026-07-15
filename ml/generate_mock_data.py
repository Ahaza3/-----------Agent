"""
电力负荷预测 — 模拟数据生成器

生成 2 年逐小时负荷模拟数据（~17,520 条），特征：
- 日负荷曲线：双峰形态（早高峰 ~10:00 + 晚高峰 ~18:00）
- 谷值时段：凌晨 04:00
- 随机噪声：高斯分布 σ=30MW
- 周末效应：×0.85
- 季节趋势：夏季高、冬季低（±15%）

用法: python generate_mock_data.py
输出: mock_load_data.csv（可直接 INSERT 到 load_data 表）
"""

import csv
import math
import random
from datetime import datetime, timedelta

# ─── 参数配置 ───
START_DATE = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0) - timedelta(days=730)
DAYS = 730  # 2 年
HOURS_PER_DAY = 24
OUTPUT_FILE = "mock_load_data.csv"

# 典型日负荷曲线系数（24 小时，双峰形态）
HOURLY_PATTERN = [
    0.65, 0.60, 0.58, 0.55,  # 00:00–03:00 深夜低谷
    0.52, 0.55, 0.62, 0.75,  # 04:00–07:00 凌晨谷底→晨间上升
    0.88, 0.95, 1.00, 0.93,  # 08:00–11:00 早高峰（10:00 峰值）
    0.85, 0.82, 0.88, 0.92,  # 12:00–15:00 午后回落→回升
    0.98, 1.00, 0.96, 0.90,  # 16:00–19:00 晚高峰（18:00 峰值）
    0.85, 0.80, 0.75, 0.70,  # 20:00–23:00 夜间下降
]

BASE_LOAD = 1000  # 基础负荷 (MW)
NOISE_SIGMA = 30  # 噪声标准差 (MW)
WEEKEND_FACTOR = 0.85
SEASONAL_AMPLITUDE = 0.15  # ±15% 季节波动


def is_weekend(dt: datetime) -> bool:
    """周六/周日"""
    return dt.weekday() >= 5


def is_holiday(dt: datetime) -> bool:
    """简化节假日判断：仅处理元旦、五一、国庆"""
    m, d = dt.month, dt.day
    return (m == 1 and d <= 3) or (m == 5 and d <= 5) or (m == 10 and d <= 7)


def seasonal_factor(day_of_year: int) -> float:
    """季节系数：夏季高、冬季低"""
    return 1.0 + SEASONAL_AMPLITUDE * math.sin(2 * math.pi * (day_of_year - 180) / 365)


def generate():
    rows = []
    for day_offset in range(DAYS):
        current_date = START_DATE + timedelta(days=day_offset)
        day_of_year = current_date.timetuple().tm_yday
        weekend = is_weekend(current_date)
        holiday = is_holiday(current_date)

        for hour in range(HOURS_PER_DAY):
            dt = current_date + timedelta(hours=hour)

            # 基础负荷 = 基准值 × 小时系数
            base = BASE_LOAD * HOURLY_PATTERN[hour]

            # 周末/节假日折扣
            if holiday:
                base *= WEEKEND_FACTOR * 0.9
            elif weekend:
                base *= WEEKEND_FACTOR

            # 季节趋势
            base *= seasonal_factor(day_of_year)

            # 高斯噪声
            noise = random.gauss(0, NOISE_SIGMA)
            load_mw = round(max(0, base + noise), 1)

            # 温度模拟（简化为正弦波 + 噪声）
            temp_base = 15 + 12 * math.sin(2 * math.pi * (day_of_year - 105) / 365)
            temperature = round(temp_base + random.gauss(0, 3) + (5 if hour >= 8 and hour <= 17 else 0), 1)

            # 湿度模拟
            humidity = round(50 + 20 * math.sin(2 * math.pi * (day_of_year + 60) / 365) + random.gauss(0, 8), 1)
            humidity = max(0, min(100, humidity))

            rows.append({
                "time": dt.strftime("%Y-%m-%d %H:%M:%S"),
                "load_mw": load_mw,
                "temperature": temperature,
                "humidity": humidity,
                "is_holiday": 1 if holiday else 0,
                "hour": hour,
                "day_of_week": dt.weekday(),  # 0=周一
                "month": dt.month,
            })

    # 写入 CSV
    with open(OUTPUT_FILE, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)

    print(f"✅ 模拟数据生成完成: {len(rows)} 条 → {OUTPUT_FILE}")
    print(f"   日期范围: {rows[0]['time']} ~ {rows[-1]['time']}")
    print(f"   负荷范围: {min(r['load_mw'] for r in rows):.0f} – {max(r['load_mw'] for r in rows):.0f} MW")


if __name__ == "__main__":
    generate()
