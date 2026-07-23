"""训练 LSTM 负荷预测模型并导出 TorchScript 模型文件。"""

import argparse
import hashlib
import json
import os
import pickle
import shutil
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from torch.optim import AdamW
from torch.optim.lr_scheduler import ReduceLROnPlateau

from sliding_window_dataset import create_dataloaders


class LSTMLoadPredictor(nn.Module):
    """带可选未来天气协变量分支的 LSTM 编码器。"""

    def __init__(
        self,
        input_size: int,
        hidden_size: int = 128,
        num_layers: int = 2,
        forecast_horizon: int = 24,
        future_weather_features: int = 0,
        dropout: float = 0.2,
    ):
        super().__init__()
        self.forecast_horizon = forecast_horizon
        self.future_weather_features = future_weather_features
        self.lstm = nn.LSTM(
            input_size=input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            dropout=dropout if num_layers > 1 else 0.0,
            batch_first=True,
        )
        if future_weather_features > 0:
            self.weather_encoder = nn.Sequential(
                nn.Linear(forecast_horizon * future_weather_features, hidden_size),
                nn.ReLU(),
                nn.Dropout(dropout),
            )
            output_size = hidden_size * 2
        else:
            self.weather_encoder = None
            output_size = hidden_size
        self.dropout = nn.Dropout(dropout)
        self.fc = nn.Linear(output_size, forecast_horizon)

    def forward(self, x: torch.Tensor, future_weather=None) -> torch.Tensor:
        out, _ = self.lstm(x)
        load_context = self.dropout(out[:, -1, :])

        if self.future_weather_features > 0:
            if future_weather is None:
                weather_context = torch.zeros_like(load_context)
            else:
                weather_context = self.weather_encoder(future_weather.reshape(x.size(0), -1))
            load_context = torch.cat([load_context, weather_context], dim=1)

        return self.fc(load_context)


def export_torchscript(
    model: nn.Module,
    input_size: int,
    seq_length: int,
    forecast_horizon: int,
    future_weather_features: int,
    save_path: str = "models/lstm_scripted.pt",
) -> None:
    model.eval()
    example_x = torch.randn(1, seq_length, input_size)
    example_weather = torch.randn(1, forecast_horizon, future_weather_features)
    with torch.no_grad():
        traced = torch.jit.trace(model, (example_x, example_weather))
    traced = torch.jit.freeze(traced)
    traced.save(save_path)
    print(f"TorchScript 模型已导出: {save_path}")


def train_epoch(model, loader, optimizer, criterion, device):
    model.train()
    total_loss = 0.0
    for x, future_weather, y in loader:
        x = x.to(device)
        future_weather = future_weather.to(device)
        y = y.to(device)
        optimizer.zero_grad()
        pred = model(x, future_weather)
        loss = criterion(pred, y)
        loss.backward()
        optimizer.step()
        total_loss += loss.item() * x.size(0)
    return total_loss / len(loader.dataset)


def evaluate_model(model, loader, criterion, device):
    model.eval()
    total_loss = 0.0
    all_preds, all_targets = [], []
    with torch.no_grad():
        for x, future_weather, y in loader:
            x = x.to(device)
            future_weather = future_weather.to(device)
            y = y.to(device)
            pred = model(x, future_weather)
            loss = criterion(pred, y)
            total_loss += loss.item() * x.size(0)
            all_preds.append(pred.cpu().numpy())
            all_targets.append(y.cpu().numpy())
    preds = np.concatenate(all_preds)
    targets = np.concatenate(all_targets)
    return total_loss / len(loader.dataset), preds, targets


def train_model(
    train_loader,
    val_loader,
    input_size: int,
    future_weather_features: int,
    device,
    scaler_y,
    epochs: int = 80,
    lr: float = 0.001,
    patience: int = 10,
    forecast_horizon: int = 24,
) -> tuple[nn.Module, dict]:
    model = LSTMLoadPredictor(
        input_size=input_size,
        hidden_size=128,
        num_layers=2,
        forecast_horizon=forecast_horizon,
        future_weather_features=future_weather_features,
        dropout=0.2,
    ).to(device)

    optimizer = AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    scheduler = ReduceLROnPlateau(optimizer, mode="min", factor=0.5, patience=5)
    criterion = nn.MSELoss()

    best_val_loss = float("inf")
    best_state = None
    patience_counter = 0

    for epoch in range(epochs):
        train_loss = train_epoch(model, train_loader, optimizer, criterion, device)
        val_loss, preds_scaled, targets_scaled = evaluate_model(
            model, val_loader, criterion, device
        )
        scheduler.step(val_loss)

        preds = scaler_y.inverse_transform(preds_scaled)
        targets = scaler_y.inverse_transform(targets_scaled)
        mape = np.mean(
            np.abs((targets - preds) / np.clip(targets, 1, None))
        ) * 100

        if epoch % 5 == 0 or epoch == epochs - 1:
            print(
                f"Epoch {epoch:3d} | train_loss={train_loss:.4f} | "
                f"val_loss={val_loss:.4f} | val_mape={mape:.2f}%"
            )

        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best_state = {k: v.cpu().clone() for k, v in model.state_dict().items()}
            patience_counter = 0
        else:
            patience_counter += 1
            if patience_counter >= patience:
                print(f"提前停止训练，当前轮次: {epoch}")
                break

    if best_state is None:
        raise RuntimeError("训练未生成有效的验证集检查点")
    model.load_state_dict(best_state)
    return model, {"val_loss": best_val_loss}


def main():
    parser = argparse.ArgumentParser(description="训练 LSTM 模型并导出 TorchScript")
    parser.add_argument("--input", default="featured_load_data.csv", help="特征 CSV 文件路径")
    parser.add_argument("--seq_length", type=int, default=168, help="历史窗口长度，单位为小时")
    parser.add_argument("--horizon", type=int, default=24, help="预测时长，单位为小时")
    parser.add_argument("--batch_size", type=int, default=64, help="批次大小")
    parser.add_argument("--epochs", type=int, default=80, help="最大训练轮数")
    parser.add_argument("--lr", type=float, default=0.001, help="学习率")
    parser.add_argument("--no-export", action="store_true", help="跳过 TorchScript 导出")
    parser.add_argument("--output-root", default="models", help="不可变模型版本目录根路径")
    args = parser.parse_args()

    if not os.path.exists(args.input):
        print(f"输入文件不存在: {args.input}")
        sys.exit(1)

    df = pd.read_csv(args.input)
    print(f"已加载训练数据: {len(df)} 行 x {len(df.columns)} 列")

    (
        train_loader,
        val_loader,
        scaler_x,
        scaler_y,
        weather_scaler,
        feature_cols,
    ) = create_dataloaders(
        df,
        target_col="load_mw",
        seq_length=args.seq_length,
        forecast_horizon=args.horizon,
        batch_size=args.batch_size,
        train_ratio=0.85,
    )

    x_sample, weather_sample, _ = next(iter(train_loader))
    input_size = x_sample.shape[-1]
    future_weather_features = weather_sample.shape[-1]
    print(
        f"历史窗口={args.seq_length} 小时，预测时长={args.horizon} 小时，"
        f"输入特征数={input_size}，未来天气特征数={future_weather_features}"
    )
    print(f"训练样本数={len(train_loader.dataset)}")
    print(f"验证样本数={len(val_loader.dataset)}")

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"开始训练 LSTM，运行设备: {device}")
    model, _ = train_model(
        train_loader,
        val_loader,
        input_size,
        future_weather_features,
        device,
        scaler_y,
        epochs=args.epochs,
        lr=args.lr,
        forecast_horizon=args.horizon,
    )

    _, preds_scaled, targets_scaled = evaluate_model(
        model, val_loader, nn.MSELoss(), device
    )
    preds = scaler_y.inverse_transform(preds_scaled)
    targets = scaler_y.inverse_transform(targets_scaled)

    mae = np.mean(np.abs(targets - preds))
    rmse = np.sqrt(np.mean((targets - preds) ** 2))
    mape = np.mean(np.abs((targets - preds) / np.clip(targets, 1, None))) * 100
    hourly_mape = np.mean(
        np.abs((targets - preds) / np.clip(targets, 1, None)), axis=0
    ) * 100

    print("LSTM 验证集指标:")
    print(f"MAE  = {mae:.2f} MW")
    print(f"RMSE = {rmse:.2f} MW")
    print(f"MAPE = {mape:.2f}%")
    print(
        f"分小时 MAPE: 1 小时={hourly_mape[0]:.2f}%  "
        f"6 小时={hourly_mape[5]:.2f}%  12 小时={hourly_mape[11]:.2f}%  "
        f"24 小时={hourly_mape[23]:.2f}%"
    )

    version = "train-" + datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
    root = Path(args.output_root).resolve()
    root.mkdir(parents=True, exist_ok=True)
    final_dir = root / version
    if final_dir.exists():
        raise RuntimeError(f"模型版本目录已存在: {version}")
    temp_dir = Path(tempfile.mkdtemp(prefix=f".{version}.", dir=root))
    torch.save(model.state_dict(), temp_dir / "lstm_model.pt")
    torch.save(
        {
            "input_size": input_size,
            "hidden_size": 128,
            "num_layers": 2,
            "seq_length": args.seq_length,
            "forecast_horizon": args.horizon,
            "future_weather_features": future_weather_features,
            "future_weather_feature_names": ["temperature", "humidity"],
        },
        temp_dir / "meta.pt",
    )

    with open(temp_dir / "scaler_y.pkl", "wb") as f:
        pickle.dump(scaler_y, f)
    with open(temp_dir / "scaler_x.pkl", "wb") as f:
        pickle.dump(scaler_x, f)
    with open(temp_dir / "weather_scaler.pkl", "wb") as f:
        pickle.dump(weather_scaler, f)
    with open(temp_dir / "feature_cols.pkl", "wb") as f:
        pickle.dump(feature_cols, f)

    if args.no_export:
        shutil.rmtree(temp_dir)
        raise RuntimeError("版本化产物必须导出 TorchScript")
    export_torchscript(model, input_size, args.seq_length, args.horizon,
                       future_weather_features, save_path=str(temp_dir / "model.pt"))
    files = []
    for path in sorted(temp_dir.rglob("*")):
        if path.is_file() and path.name != "manifest.json":
            relative = path.relative_to(temp_dir).as_posix()
            files.append({"path": relative, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    checksum_source = "".join(f"{entry['path']}\n{entry['sha256']}\n" for entry in files)
    manifest = {
        "modelVersion": version,
        "modelType": "LSTM",
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "framework": {"runtime": "torch", "torchVersion": torch.__version__},
        "files": files,
        "artifactChecksum": hashlib.sha256(checksum_source.encode("utf-8")).hexdigest(),
    }
    (temp_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(temp_dir, final_dir)
    print(f"MODEL_ARTIFACT_DIR={version}")


if __name__ == "__main__":
    main()
