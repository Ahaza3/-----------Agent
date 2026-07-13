"""
电力负荷预测 — Prophet 基线模型训练

Prophet 是 Meta 开源的时间序列预测工具，适合电力负荷这种
具有强周期性的数据（日周期、周周期、年周期）。

用例:
    python train_prophet.py [--input featured_load_data.csv]

产出:
    models/prophet_model.pkl — 训练好的 Prophet 模型
"""

import argparse
import os
import pickle
import sys

import numpy as np
import pandas as pd
from prophet import Prophet
from sklearn.metrics import mean_absolute_error, mean_squared_error


def load_data(csv_path: str) -> pd.DataFrame:
    """加载特征工程后的数据，取 Prophet 需要的列"""
    df = pd.read_csv(csv_path)
    df["time"] = pd.to_datetime(df["time"])
    df = df.sort_values("time").reset_index(drop=True)

    # Prophet 要求列名为 ds (时间) 和 y (目标)
    prophet_df = df[["time", "load_mw", "temperature", "humidity", "is_holiday"]].copy()
    prophet_df.rename(columns={"time": "ds", "load_mw": "y"}, inplace=True)
    return prophet_df


def train_prophet(train_df: pd.DataFrame) -> Prophet:
    """训练 Prophet 模型"""
    model = Prophet(
        changepoint_prior_scale=0.05,
        seasonality_prior_scale=10.0,
        holidays_prior_scale=10.0,
        yearly_seasonality=True,
        weekly_seasonality=True,
        daily_seasonality=False,
        interval_width=0.80,
    )

    # 自定义日周期（24 小时）
    model.add_seasonality(name="hourly", period=1, fourier_order=5)

    # 添加气象外生变量
    model.add_regressor("temperature")
    model.add_regressor("humidity")
    model.add_regressor("is_holiday")

    model.fit(train_df, iter=200)
    return model


def evaluate(model: Prophet, test_df: pd.DataFrame) -> dict:
    """在测试集上评估模型"""
    forecast = model.predict(test_df)

    y_true = test_df["y"].values
    y_pred = forecast["yhat"].values

    # 过滤 NaN（trend 未 cover 的区间可能有 NaN）
    mask = ~np.isnan(y_pred)
    y_true = y_true[mask]
    y_pred = y_pred[mask]

    mae = mean_absolute_error(y_true, y_pred)
    rmse = np.sqrt(mean_squared_error(y_true, y_pred))
    mape = np.mean(np.abs((y_true - y_pred) / np.clip(y_true, 1, None))) * 100

    return {"mae": mae, "rmse": rmse, "mape": mape, "n_samples": len(y_true)}


def main():
    parser = argparse.ArgumentParser(description="Prophet 基线模型训练")
    parser.add_argument("--input", default="featured_load_data.csv", help="特征 CSV 路径")
    args = parser.parse_args()

    # 1. 加载数据
    if not os.path.exists(args.input):
        print(f"❌ 输入文件不存在: {args.input}")
        print("   请先运行 feature_engineering.py 生成特征数据")
        sys.exit(1)

    df = load_data(args.input)
    print(f"📂 加载数据: {len(df)} 行")

    # 2. 时序分割（85% 训练 / 15% 测试）
    split_idx = int(len(df) * 0.85)
    train_df = df.iloc[:split_idx]
    test_df = df.iloc[split_idx:]
    print(f"   训练集: {len(train_df)} 行 ({train_df['ds'].min()} ~ {train_df['ds'].max()})")
    print(f"   测试集: {len(test_df)} 行 ({test_df['ds'].min()} ~ {test_df['ds'].max()})")

    # 3. 训练
    print("\n⏳ 训练 Prophet 中...")
    model = train_prophet(train_df)
    print("✅ 训练完成")

    # 4. 评估
    metrics = evaluate(model, test_df)
    print(f"\n📊 Prophet 测试集评估结果:")
    print(f"   MAE  = {metrics['mae']:.2f} MW")
    print(f"   RMSE = {metrics['rmse']:.2f} MW")
    print(f"   MAPE = {metrics['mape']:.2f}%")
    print(f"   样本数 = {metrics['n_samples']}")

    if metrics["mape"] < 8.0:
        print(f"   ✅ MAPE {metrics['mape']:.2f}% < 8%，达标！")
    else:
        print(f"   ⚠️ MAPE {metrics['mape']:.2f}% ≥ 8%，未达标")

    # 5. 保存模型
    os.makedirs("models", exist_ok=True)
    model_path = "models/prophet_model.pkl"
    with open(model_path, "wb") as f:
        pickle.dump(model, f)
    print(f"\n💾 模型已保存: {model_path}")


if __name__ == "__main__":
    main()
