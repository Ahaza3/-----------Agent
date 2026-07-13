"""
电力负荷预测 — LSTM 模型定义 + 训练 + TorchScript 导出

使用 Day5 的 sliding_window_dataset 加载数据，训练双层 LSTM，
直接输出未来 24h 负荷，并导出为 TorchScript 供 Flask 推理使用。

用例:
    python train_lstm.py [--input featured_load_data.csv]

产出:
    models/lstm_model.pt      — 训练好的模型权重（state_dict）
    models/lstm_meta.pt       — 模型结构元信息
    models/lstm_scripted.pt   — TorchScript 导出（Flask 直接加载）
"""

import argparse
import os
import sys

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from torch.optim import AdamW
from torch.optim.lr_scheduler import ReduceLROnPlateau

# Day5 滑动窗口数据集
from sliding_window_dataset import create_dataloaders


# ─── LSTM 模型定义 ───

class LSTMLoadPredictor(nn.Module):
    """
    双层 LSTM + 全连接输出层，直接输出未来 24h 负荷

    Args:
        input_size:       输入特征维度
        hidden_size:      隐层大小
        num_layers:       LSTM 层数
        forecast_horizon: 预测步数（默认 24）
        dropout:          Dropout 比例
    """

    def __init__(self, input_size: int, hidden_size: int = 128,
                 num_layers: int = 2, forecast_horizon: int = 24,
                 dropout: float = 0.2):
        super().__init__()
        self.forecast_horizon = forecast_horizon
        self.lstm = nn.LSTM(
            input_size=input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            dropout=dropout if num_layers > 1 else 0.0,
            batch_first=True,
        )
        self.fc = nn.Linear(hidden_size, forecast_horizon)
        self.dropout = nn.Dropout(dropout)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: (batch, seq_len, input_size)
        out, _ = self.lstm(x)          # (batch, seq_len, hidden_size)
        out = out[:, -1, :]            # 取最后一步 (batch, hidden_size)
        out = self.dropout(out)
        out = self.fc(out)             # (batch, forecast_horizon)
        return out


# ─── TorchScript 导出 ───

def export_torchscript(model: nn.Module, input_size: int, seq_length: int,
                       save_path: str = "models/lstm_scripted.pt") -> None:
    """
    将训练好的模型导出为 TorchScript

    TorchScript 可脱离 Python 类定义独立运行，
    推理时只需 torch.jit.load() 无需 import LSTMLoadPredictor。
    """
    model.eval()
    example = torch.randn(1, seq_length, input_size)
    with torch.no_grad():
        traced = torch.jit.trace(model, example)
    # 优化：常量折叠 + 死代码消除
    traced = torch.jit.freeze(traced)
    traced.save(save_path)
    print(f"✅ TorchScript 模型已导出: {save_path}")


# ─── 训练 + 评估 ───

def train_epoch(model, loader, optimizer, criterion, device):
    model.train()
    total_loss = 0.0
    for x, y in loader:
        x, y = x.to(device), y.to(device)
        optimizer.zero_grad()
        pred = model(x)
        loss = criterion(pred, y)
        loss.backward()
        optimizer.step()
        total_loss += loss.item() * x.size(0)
    return total_loss / len(loader.dataset)  # type: ignore[arg-type]


def evaluate_model(model, loader, criterion, device):
    model.eval()
    total_loss = 0.0
    all_preds, all_targets = [], []
    with torch.no_grad():
        for x, y in loader:
            x, y = x.to(device), y.to(device)
            pred = model(x)                     # (batch, 24)
            loss = criterion(pred, y)           # y: (batch, 24)
            total_loss += loss.item() * x.size(0)
            all_preds.append(pred.cpu().numpy())
            all_targets.append(y.cpu().numpy())
    preds = np.concatenate(all_preds)           # (N, 24)
    targets = np.concatenate(all_targets)       # (N, 24)
    return total_loss / len(loader.dataset), preds, targets  # type: ignore[arg-type]


def train_model(
    train_loader, val_loader, input_size: int, device, scaler_y,
    epochs: int = 80, lr: float = 0.001, patience: int = 10,
    forecast_horizon: int = 24,
) -> tuple[nn.Module, dict]:
    """训练 LSTM，带早停"""

    model = LSTMLoadPredictor(
        input_size=input_size, hidden_size=128, num_layers=2,
        forecast_horizon=forecast_horizon, dropout=0.2,
    ).to(device)

    optimizer = AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    scheduler = ReduceLROnPlateau(optimizer, mode="min", factor=0.5, patience=5)
    criterion = nn.MSELoss()

    best_val_loss = float("inf")
    best_state = None
    patience_counter = 0

    for epoch in range(epochs):
        train_loss = train_epoch(model, train_loader, optimizer, criterion, device)
        val_loss, preds_scaled, targets_scaled = evaluate_model(model, val_loader, criterion, device)

        scheduler.step(val_loss)

        # 反标准化计算 MAPE（对 24 个时间点取平均）
        preds = scaler_y.inverse_transform(preds_scaled)       # (N, 24)
        targets = scaler_y.inverse_transform(targets_scaled)   # (N, 24)
        mape = np.mean(np.abs((targets - preds) / np.clip(targets, 1, None))) * 100

        if epoch % 5 == 0 or epoch == epochs - 1:
            print(f"  Epoch {epoch:3d} | train_loss={train_loss:.4f} | "
                  f"val_loss={val_loss:.4f} | val_mape={mape:.2f}%")

        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best_state = {k: v.cpu().clone() for k, v in model.state_dict().items()}
            patience_counter = 0
        else:
            patience_counter += 1
            if patience_counter >= patience:
                print(f"  ⏹ 早停于 epoch {epoch}")
                break

    model.load_state_dict(best_state)
    return model, {"val_loss": best_val_loss}


def main():
    parser = argparse.ArgumentParser(description="LSTM 模型训练 + TorchScript 导出")
    parser.add_argument("--input", default="featured_load_data.csv", help="特征 CSV 路径")
    parser.add_argument("--seq_length", type=int, default=168, help="输入序列长度（小时）")
    parser.add_argument("--horizon", type=int, default=24, help="预测步数（小时）")
    parser.add_argument("--batch_size", type=int, default=64, help="批次大小")
    parser.add_argument("--epochs", type=int, default=80, help="最大训练轮数")
    parser.add_argument("--lr", type=float, default=0.001, help="学习率")
    parser.add_argument("--no-export", action="store_true", help="跳过 TorchScript 导出")
    args = parser.parse_args()

    # 1. 加载数据
    if not os.path.exists(args.input):
        print(f"❌ 输入文件不存在: {args.input}")
        print("   请先运行 feature_engineering.py 生成特征数据")
        sys.exit(1)

    df = pd.read_csv(args.input)
    print(f"📂 加载数据: {len(df)} 行 × {len(df.columns)} 列")

    # 2. 构建 DataLoader（Day5 接口）
    train_loader, val_loader, scaler_y = create_dataloaders(
        df,
        target_col="load_mw",
        seq_length=args.seq_length,
        forecast_horizon=args.horizon,
        batch_size=args.batch_size,
        train_ratio=0.85,
    )

    # 获取输入特征维度
    x_sample, _ = next(iter(train_loader))
    input_size = x_sample.shape[-1]

    print(f"   seq_length={args.seq_length}, horizon={args.horizon}, input_size={input_size}")
    print(f"   训练集: {len(train_loader.dataset)} 样本, {len(train_loader)} batches")  # type: ignore[arg-type]
    print(f"   验证集: {len(val_loader.dataset)} 样本, {len(val_loader)} batches")      # type: ignore[arg-type]

    # 3. 训练
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"\n⏳ 训练 LSTM (device={device})...")
    model, _ = train_model(
        train_loader, val_loader, input_size, device, scaler_y,
        epochs=args.epochs, lr=args.lr, forecast_horizon=args.horizon,
    )

    # 4. 最终评估
    _, preds_scaled, targets_scaled = evaluate_model(
        model, val_loader, nn.MSELoss(), device
    )
    preds = scaler_y.inverse_transform(preds_scaled)
    targets = scaler_y.inverse_transform(targets_scaled)

    mae = np.mean(np.abs(targets - preds))
    rmse = np.sqrt(np.mean((targets - preds) ** 2))
    mape = np.mean(np.abs((targets - preds) / np.clip(targets, 1, None))) * 100

    # 逐小时 MAPE
    hourly_mape = np.mean(np.abs((targets - preds) / np.clip(targets, 1, None)), axis=0) * 100

    print(f"\n📊 LSTM 验证集最终评估:")
    print(f"   MAE  = {mae:.2f} MW")
    print(f"   RMSE = {rmse:.2f} MW")
    print(f"   MAPE = {mape:.2f}%")
    print(f"   逐小时 MAPE: 1h={hourly_mape[0]:.2f}%  6h={hourly_mape[5]:.2f}%  "
          f"12h={hourly_mape[11]:.2f}%  24h={hourly_mape[23]:.2f}%")

    if mape < 5.0:
        print(f"   ✅ MAPE {mape:.2f}% < 5%，达标！")
    else:
        print(f"   ⚠️ MAPE {mape:.2f}% ≥ 5%，未达标")

    # 5. 保存模型
    os.makedirs("models", exist_ok=True)

    # state_dict（可恢复训练）
    model_path = "models/lstm_model.pt"
    torch.save(model.state_dict(), model_path)
    print(f"\n💾 模型权重已保存: {model_path}")

    # 元信息（含 seq_length 供推理使用）
    meta_path = "models/lstm_meta.pt"
    torch.save({
        "input_size": input_size, "hidden_size": 128,
        "num_layers": 2, "seq_length": args.seq_length,
        "forecast_horizon": args.horizon,
    }, meta_path)
    print(f"💾 模型元信息已保存: {meta_path}")

    # target scaler（供 Flask 反标准化）
    import pickle
    scaler_path = "models/lstm_scaler.pkl"
    with open(scaler_path, "wb") as f:
        pickle.dump(scaler_y, f)
    print(f"💾 Scaler 已保存: {scaler_path}")

    # 6. TorchScript 导出（供 Flask 推理）
    if not args.no_export:
        export_torchscript(
            model, input_size, args.seq_length,
            save_path="models/lstm_scripted.pt",
        )


if __name__ == "__main__":
    main()
