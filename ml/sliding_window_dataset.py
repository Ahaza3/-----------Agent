"""Sliding-window dataset for load forecasting."""

import argparse
import os

import numpy as np
import pandas as pd
import torch
from sklearn.preprocessing import StandardScaler
from torch.utils.data import DataLoader, Dataset


DEFAULT_WEATHER_COLS = ["temperature", "humidity"]


class SlidingWindowDataset(Dataset):
    """Return historical features, future weather covariates, and targets."""

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
    """Build normalized historical and future-weather windows."""
    if feature_cols is None:
        exclude = {target_col, "time", "created_at"}
        feature_cols = [
            c for c in df.select_dtypes(include=[np.number]).columns
            if c not in exclude
        ]

    weather_cols = weather_cols or DEFAULT_WEATHER_COLS
    missing_weather = [col for col in weather_cols if col not in df.columns]
    if missing_weather:
        raise ValueError(f"missing weather columns: {', '.join(missing_weather)}")

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
            f"need more data than seq_length={seq_length} + horizon={forecast_horizon}, got {len(data)}"
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
    """Build time-ordered train and validation DataLoaders."""
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
        raise ValueError("train_ratio must leave both train and validation samples")

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
    parser = argparse.ArgumentParser(description="Build and inspect sliding-window datasets")
    parser.add_argument("--input", default="featured_load_data.csv", help="feature CSV path")
    parser.add_argument("--seq_length", type=int, default=168, help="historical window in hours")
    parser.add_argument("--batch_size", type=int, default=64, help="batch size")
    parser.add_argument("--train_ratio", type=float, default=0.85, help="train split ratio")
    args = parser.parse_args()

    if not os.path.exists(args.input):
        print(f"Input file not found: {args.input}")
        return

    df = pd.read_csv(args.input)
    print(f"Loaded feature data: {len(df)} rows x {len(df.columns)} columns")

    train_loader, val_loader, _, scaler_y, _, feature_cols = create_dataloaders(
        df,
        target_col="load_mw",
        seq_length=args.seq_length,
        batch_size=args.batch_size,
        train_ratio=args.train_ratio,
    )

    print("Sliding-window dataset ready")
    print(f"  seq_length = {args.seq_length}h")
    print(f"  features = {len(feature_cols)}")
    print(f"  train = {len(train_loader.dataset)} samples, {len(train_loader)} batches")
    print(f"  validation = {len(val_loader.dataset)} samples, {len(val_loader)} batches")

    x, weather, y = next(iter(train_loader))
    print(f"  X shape: {x.shape}")
    print(f"  future weather shape: {weather.shape}")
    print(f"  y shape: {y.shape}")
    print(
        "  y sample:",
        scaler_y.inverse_transform(y[:3].numpy().reshape(-1, 1)).flatten(),
    )


if __name__ == "__main__":
    main()
