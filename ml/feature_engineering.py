"""
电力负荷预测 — 特征工程脚本

从模拟数据生成训练特征，包括：
1. 时间特征：hour / day_of_week / month 的 sin/cos 循环编码
2. 滞后特征：t-1, t-24, t-168（1 小时前、1 天前、1 周前）
3. 滚动统计特征：过去 24h 均值、标准差

用法: python feature_engineering.py [--input mock_load_data.csv] [--output featured_load_data.csv]
"""

import argparse
import math
import os

import numpy as np
import pandas as pd


def add_temporal_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    添加时间循环特征（sin/cos 编码），保留周期性信息。

    使用 sin/cos 编码的原因：hour=23 和 hour=0 在数值上相差 23，
    但实际只差 1 小时。sin/cos 将其映射到连续圆上，捕捉真实距离。
    """
    # hour: 0-23
    df["hour_sin"] = np.sin(2 * math.pi * df["hour"] / 24)
    df["hour_cos"] = np.cos(2 * math.pi * df["hour"] / 24)

    # day_of_week: 0-6
    df["dow_sin"] = np.sin(2 * math.pi * df["day_of_week"] / 7)
    df["dow_cos"] = np.cos(2 * math.pi * df["day_of_week"] / 7)

    # month: 1-12
    df["month_sin"] = np.sin(2 * math.pi * df["month"] / 12)
    df["month_cos"] = np.cos(2 * math.pi * df["month"] / 12)

    return df


def add_lag_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    添加滞后特征（t-1, t-24, t-168）。

    由于数据是逐小时连续的，lag=1 即为 1 小时前，
    lag=24 为 1 天前同一时刻，lag=168 为 1 周前同一时刻。
    """
    df["load_lag_1"] = df["load_mw"].shift(1)
    df["load_lag_24"] = df["load_mw"].shift(24)
    df["load_lag_168"] = df["load_mw"].shift(168)

    # 温度滞后
    df["temp_lag_1"] = df["temperature"].shift(1)
    df["temp_lag_24"] = df["temperature"].shift(24)

    return df


def add_rolling_features(df: pd.DataFrame) -> pd.DataFrame:
    """添加滚动窗口统计特征"""
    # 过去 24 小时均值
    df["load_roll_mean_24"] = df["load_mw"].rolling(window=24, min_periods=1).mean()
    # 过去 24 小时标准差
    df["load_roll_std_24"] = df["load_mw"].rolling(window=24, min_periods=1).std().fillna(0)

    return df


def add_time_index(df: pd.DataFrame) -> pd.DataFrame:
    """解析 time 列为 datetime，并按时间排序"""
    df["time"] = pd.to_datetime(df["time"])
    df = df.sort_values("time").reset_index(drop=True)
    return df


def main():
    parser = argparse.ArgumentParser(description="负荷预测特征工程")
    parser.add_argument("--input", default="mock_load_data.csv", help="输入 CSV 路径")
    parser.add_argument("--output", default="featured_load_data.csv", help="输出 CSV 路径")
    args = parser.parse_args()

    # 1. 加载数据
    if not os.path.exists(args.input):
        print(f"❌ 输入文件不存在: {args.input}")
        print("   请先运行 generate_mock_data.py 生成模拟数据")
        return

    df = pd.read_csv(args.input)
    print(f"📂 加载数据: {len(df)} 行, {len(df.columns)} 列")

    # 2. 时间解析与排序
    df = add_time_index(df)

    # 3. 特征工程
    df = add_temporal_features(df)
    df = add_lag_features(df)
    df = add_rolling_features(df)

    # 4. 填充 lag 导致的 NaN（前 168 行）
    numeric_cols = df.select_dtypes(include=[np.number]).columns
    df[numeric_cols] = df[numeric_cols].fillna(method="bfill").fillna(method="ffill")

    # 5. 输出
    df.to_csv(args.output, index=False, encoding="utf-8")
    print(f"✅ 特征工程完成: {len(df)} 行, {len(df.columns)} 列 → {args.output}")
    print(f"   特征列: {list(df.columns)}")
    print(f"   时间范围: {df['time'].min()} ~ {df['time'].max()}")


if __name__ == "__main__":
    main()
