"""负荷预测滑动窗口数据集。"""

import argparse
import os

import numpy as np
import pandas as pd
import torch
from sklearn.preprocessing import StandardScaler
from torch.utils.data import DataLoader, Dataset


DEFAULT_WEATHER_COLS = ["temperature", "humidity"]


class SlidingWindowDataset(Dataset):
    """返回历史特征、未来天气协变量和预测目标。"""

    def __init__(
        self,
        features: np.ndarray,
        future_weather: np.ndarray,
        targets: np.ndarray,
    ):
        self.features = torch.from_numpy(features)
        self.future_weather = torch.from_numpy(future_weather)
        self.targets = torch.from_numpy(targets)

    def __len__(self):
        return len(self.targets)

    def __getitem__(self, idx):
        return self.features[idx], self.future_weather[idx], self.targets[idx]


def build_sliding_windows(
    df: pd.DataFrame,
    target_col: str = "load_mw",
    seq_length: int = 168,
    forecast_horizon: int = 24,
    feature_cols: list | None = None,
    weather_cols: list[str] | None = None,
) -> tuple:
    """构建标准化的历史特征窗口和未来天气窗口。"""
    if feature_cols is None:
        exclude = {target_col, "time", "created_at"}
        feature_cols = [
            c for c in df.select_dtypes(include=[np.number]).columns
            if c not in exclude
        ]

    weather_cols = weather_cols or DEFAULT_WEATHER_COLS
    missing_weather = [col for col in weather_cols if col not in df.columns]
    if missing_weather:
        raise ValueError(f"缺少天气字段: {', '.join(missing_weather)}")

    data = df[feature_cols].apply(pd.to_numeric, errors="coerce").bfill().ffill().fillna(0).values
    raw_y = pd.to_numeric(df[target_col], errors="coerce").bfill().ffill().fillna(0).values.astype(np.float32)
    raw_weather = (
        df[weather_cols]
        .apply(pd.to_numeric, errors="coerce")
        .bfill()
        .ffill()
        .fillna(0)
        .values
        .astype(np.float32)
    )

    n_samples = len(data) - seq_length - forecast_horizon + 1
    if n_samples <= 0:
        raise ValueError(
            f"数据量不足，至少需要 seq_length={seq_length} + horizon={forecast_horizon}，实际为 {len(data)}"
        )

    scaler_x = StandardScaler()
    data_scaled = scaler_x.fit_transform(data).astype(np.float32)

    scaler_y = StandardScaler()
    targets_scaled = scaler_y.fit_transform(raw_y.reshape(-1, 1)).flatten().astype(np.float32)

    weather_scaler = StandardScaler()
    weather_scaled = weather_scaler.fit_transform(raw_weather).astype(np.float32)

    features = np.zeros((n_samples, seq_length, len(feature_cols)), dtype=np.float32)
    future_weather = np.zeros(
        (n_samples, forecast_horizon, len(weather_cols)), dtype=np.float32
    )
    targets = np.zeros((n_samples, forecast_horizon), dtype=np.float32)
    raw_targets_out = np.zeros((n_samples, forecast_horizon), dtype=np.float32)

    for i in range(n_samples):
        target_start = i + seq_length
        features[i] = data_scaled[i:target_start]
        future_weather[i] = weather_scaled[target_start:target_start + forecast_horizon]
        targets[i] = targets_scaled[target_start:target_start + forecast_horizon]
        raw_targets_out[i] = raw_y[target_start:target_start + forecast_horizon]

    return (
        features,
        future_weather,
        targets,
        raw_targets_out,
        scaler_x,
        scaler_y,
        weather_scaler,
        feature_cols,
    )


def create_dataloaders(
    df: pd.DataFrame,
    target_col: str = "load_mw",
    seq_length: int = 168,
    forecast_horizon: int = 24,
    batch_size: int = 64,
    train_ratio: float = 0.85,
    feature_cols: list | None = None,
    shuffle_train: bool = True,
) -> tuple:
    """按时间顺序构建训练集和验证集 DataLoader。"""
    (
        features,
        future_weather,
        targets,
        _,
        scaler_x,
        scaler_y,
        weather_scaler,
        resolved_feature_cols,
    ) = build_sliding_windows(
        df,
        target_col=target_col,
        seq_length=seq_length,
        forecast_horizon=forecast_horizon,
        feature_cols=feature_cols,
    )

    split_idx = int(len(features) * train_ratio)
    if split_idx <= 0 or split_idx >= len(features):
        raise ValueError("train_ratio 必须同时保留训练样本和验证样本")

    train_dataset = SlidingWindowDataset(
        features[:split_idx], future_weather[:split_idx], targets[:split_idx]
    )
    val_dataset = SlidingWindowDataset(
        features[split_idx:], future_weather[split_idx:], targets[split_idx:]
    )

    train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=shuffle_train)
    val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False)
    return train_loader, val_loader, scaler_x, scaler_y, weather_scaler, resolved_feature_cols


def main():
    parser = argparse.ArgumentParser(description="构建并检查负荷预测滑动窗口数据集")
    parser.add_argument("--input", default="featured_load_data.csv", help="特征 CSV 文件路径")
    parser.add_argument("--seq_length", type=int, default=168, help="历史窗口长度，单位为小时")
    parser.add_argument("--batch_size", type=int, default=64, help="批次大小")
    parser.add_argument("--train_ratio", type=float, default=0.85, help="训练集比例")
    args = parser.parse_args()

    if not os.path.exists(args.input):
        print(f"输入文件不存在: {args.input}")
        return

    df = pd.read_csv(args.input)
    print(f"已加载特征数据: {len(df)} 行 x {len(df.columns)} 列")

    train_loader, val_loader, _, scaler_y, _, feature_cols = create_dataloaders(
        df,
        target_col="load_mw",
        seq_length=args.seq_length,
        batch_size=args.batch_size,
        train_ratio=args.train_ratio,
    )

    print("滑动窗口数据集构建完成")
    print(f"  历史窗口 = {args.seq_length} 小时")
    print(f"  特征数 = {len(feature_cols)}")
    print(f"  训练集 = {len(train_loader.dataset)} 个样本，{len(train_loader)} 个批次")
    print(f"  验证集 = {len(val_loader.dataset)} 个样本，{len(val_loader)} 个批次")

    x, weather, y = next(iter(train_loader))
    print(f"  历史特征形状: {x.shape}")
    print(f"  未来天气形状: {weather.shape}")
    print(f"  目标值形状: {y.shape}")
    print(
        "  目标值示例:",
        scaler_y.inverse_transform(y[:3].numpy().reshape(-1, 1)).flatten(),
    )


if __name__ == "__main__":
    main()
