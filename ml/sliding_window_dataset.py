"""
电力负荷预测 — 滑动窗口数据集 + PyTorch DataLoader

将特征工程后的时序数据构建为监督学习样本：
- 输入 X: 过去 seq_length 小时的特征窗口  (seq_length × n_features)
- 标签 y: 下一小时的负荷值

用法（模块导入）:
    from sliding_window_dataset import SlidingWindowDataset, create_dataloaders

    train_loader, val_loader, scaler_y = create_dataloaders(
        df, seq_length=168, batch_size=64, train_ratio=0.85
    )

用法（独立测试）:
    python sliding_window_dataset.py --input featured_load_data.csv
"""

import argparse
import os

import numpy as np
import pandas as pd
import torch
from sklearn.preprocessing import StandardScaler
from torch.utils.data import DataLoader, Dataset


class SlidingWindowDataset(Dataset):
    """
    滑动窗口时序数据集

    对时序 DataFrame 按 seq_length 构建 (X, y) 对。
    X = [t-seq_length+1, ..., t] 时刻的特征
    y = t+1 时刻的负荷值
    """

    def __init__(self, features: np.ndarray, targets: np.ndarray):
        """
        Args:
            features: (N, seq_length, n_features) 的 float32 数组
            targets:  (N,) 的 float32 数组（已标准化）
        """
        self.features = torch.from_numpy(features)
        self.targets = torch.from_numpy(targets)

    def __len__(self):
        return len(self.targets)

    def __getitem__(self, idx):
        return self.features[idx], self.targets[idx]


def build_sliding_windows(
    df: pd.DataFrame,
    target_col: str = "load_mw",
    seq_length: int = 168,
    feature_cols: list | None = None,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """
    从 DataFrame 构建滑动窗口数组

    Args:
        df: 特征 DataFrame（已按时间排序）
        target_col: 预测目标列名
        seq_length: 序列长度（默认 168 = 7 天 × 24h）
        feature_cols: 特征列名列表，None 则自动选择数值列

    Returns:
        features: (N, seq_length, n_features) 标准化特征数组
        targets:  (N,) 标准化目标数组
        raw_targets: (N,) 原始目标值（反标准化用）
    """
    if feature_cols is None:
        # 自动选择：数值列，排除目标列和时间列
        exclude = {target_col, "time", "created_at"}
        feature_cols = [
            c for c in df.select_dtypes(include=[np.number]).columns
            if c not in exclude
        ]

    data = df[feature_cols].values.astype(np.float32)
    raw_y = df[target_col].values.astype(np.float32)

    n_samples = len(data) - seq_length
    if n_samples <= 0:
        raise ValueError(f"数据量 ({len(data)}) 不足以构建 seq_length={seq_length} 的窗口")

    # 特征标准化（全部特征全局拟合，保持时序顺序）
    scaler_x = StandardScaler()
    data_scaled = scaler_x.fit_transform(data)

    scaler_y = StandardScaler()
    targets_scaled = scaler_y.fit_transform(raw_y.reshape(-1, 1)).flatten()

    # 构建滑动窗口
    features = np.zeros((n_samples, seq_length, len(feature_cols)), dtype=np.float32)
    targets = np.zeros(n_samples, dtype=np.float32)
    raw_targets_out = np.zeros(n_samples, dtype=np.float32)

    for i in range(n_samples):
        features[i] = data_scaled[i:i + seq_length]
        targets[i] = targets_scaled[i + seq_length]
        raw_targets_out[i] = raw_y[i + seq_length]

    return features, targets, raw_targets_out


def create_dataloaders(
    df: pd.DataFrame,
    target_col: str = "load_mw",
    seq_length: int = 168,
    batch_size: int = 64,
    train_ratio: float = 0.85,
    feature_cols: list | None = None,
    shuffle_train: bool = True,
) -> tuple[DataLoader, DataLoader, StandardScaler]:
    """
    一站式构建训练/验证 DataLoader

    Args:
        df: 特征 DataFrame
        target_col: 预测目标列
        seq_length: 输入序列长度
        batch_size: 批次大小
        train_ratio: 训练集比例（时序分割，不打乱顺序）
        feature_cols: 特征列名
        shuffle_train: 训练集是否 shuffle

    Returns:
        (train_loader, val_loader, target_scaler)
        target_scaler 用于反标准化预测值
    """
    # 构建窗口
    features, targets, _ = build_sliding_windows(
        df, target_col=target_col, seq_length=seq_length, feature_cols=feature_cols
    )

    # 时序分割（保持时间顺序）
    split_idx = int(len(features) * train_ratio)
    train_features = features[:split_idx]
    train_targets = targets[:split_idx]
    val_features = features[split_idx:]
    val_targets = targets[split_idx:]

    train_dataset = SlidingWindowDataset(train_features, train_targets)
    val_dataset = SlidingWindowDataset(val_features, val_targets)

    train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=shuffle_train)
    val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False)

    # 拟合目标 scaler（用于反标准化）
    scaler_y = StandardScaler()
    scaler_y.fit(df[[target_col]].values)

    return train_loader, val_loader, scaler_y


def main():
    """独立运行：测试数据集构建"""
    parser = argparse.ArgumentParser(description="滑动窗口数据集构建")
    parser.add_argument("--input", default="featured_load_data.csv", help="特征 CSV 路径")
    parser.add_argument("--seq_length", type=int, default=168, help="序列长度（小时）")
    parser.add_argument("--batch_size", type=int, default=64, help="批次大小")
    parser.add_argument("--train_ratio", type=float, default=0.85, help="训练集比例")
    args = parser.parse_args()

    if not os.path.exists(args.input):
        print(f"❌ 输入文件不存在: {args.input}")
        print("   请先运行 feature_engineering.py 生成特征数据")
        return

    df = pd.read_csv(args.input)
    print(f"📂 加载特征数据: {len(df)} 行 × {len(df.columns)} 列")

    train_loader, val_loader, scaler_y = create_dataloaders(
        df,
        target_col="load_mw",
        seq_length=args.seq_length,
        batch_size=args.batch_size,
        train_ratio=args.train_ratio,
    )

    n_train = len(train_loader.dataset)  # type: ignore[arg-type]
    n_val = len(val_loader.dataset)      # type: ignore[arg-type]

    print(f"✅ 滑动窗口数据集构建完成:")
    print(f"   seq_length = {args.seq_length}h")
    print(f"   训练集: {n_train} 样本, {len(train_loader)} batches")
    print(f"   验证集: {n_val} 样本, {len(val_loader)} batches")
    print(f"   batch_size = {args.batch_size}")

    # 检查第一个 batch
    x, y = next(iter(train_loader))
    print(f"   X shape: {x.shape}  (batch, seq_len, features)")
    print(f"   y shape: {y.shape}  (batch,)")
    print(f"   y 反标准化示例 (前 3 个): {scaler_y.inverse_transform(y[:3].numpy().reshape(-1, 1)).flatten()}")


if __name__ == "__main__":
    main()
