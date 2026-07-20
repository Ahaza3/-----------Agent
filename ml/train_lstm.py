"""Train the LSTM load forecaster and export TorchScript artifacts."""

import argparse
import os
import pickle
import sys

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from torch.optim import AdamW
from torch.optim.lr_scheduler import ReduceLROnPlateau

from sliding_window_dataset import create_dataloaders


class LSTMLoadPredictor(nn.Module):
    """LSTM encoder with an optional future-weather covariate branch."""

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
    print(f"TorchScript model exported: {save_path}")


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
                print(f"Early stopping at epoch {epoch}")
                break

    if best_state is None:
        raise RuntimeError("training produced no validation checkpoint")
    model.load_state_dict(best_state)
    return model, {"val_loss": best_val_loss}


def main():
    parser = argparse.ArgumentParser(description="Train LSTM and export TorchScript")
    parser.add_argument("--input", default="featured_load_data.csv", help="feature CSV path")
    parser.add_argument("--seq_length", type=int, default=168, help="historical window in hours")
    parser.add_argument("--horizon", type=int, default=24, help="forecast horizon in hours")
    parser.add_argument("--batch_size", type=int, default=64, help="batch size")
    parser.add_argument("--epochs", type=int, default=80, help="maximum epochs")
    parser.add_argument("--lr", type=float, default=0.001, help="learning rate")
    parser.add_argument("--no-export", action="store_true", help="skip TorchScript export")
    args = parser.parse_args()

    if not os.path.exists(args.input):
        print(f"Input file not found: {args.input}")
        sys.exit(1)

    df = pd.read_csv(args.input)
    print(f"Loaded data: {len(df)} rows x {len(df.columns)} columns")

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
        f"seq_length={args.seq_length}, horizon={args.horizon}, "
        f"input_size={input_size}, future_weather_features={future_weather_features}"
    )
    print(f"train samples={len(train_loader.dataset)}")
    print(f"validation samples={len(val_loader.dataset)}")

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Training LSTM on {device}...")
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

    print("LSTM validation metrics:")
    print(f"MAE  = {mae:.2f} MW")
    print(f"RMSE = {rmse:.2f} MW")
    print(f"MAPE = {mape:.2f}%")
    print(
        f"Hourly MAPE: 1h={hourly_mape[0]:.2f}%  "
        f"6h={hourly_mape[5]:.2f}%  12h={hourly_mape[11]:.2f}%  "
        f"24h={hourly_mape[23]:.2f}%"
    )

    os.makedirs("models", exist_ok=True)
    torch.save(model.state_dict(), "models/lstm_model.pt")
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
        "models/lstm_meta.pt",
    )

    with open("models/lstm_scaler.pkl", "wb") as f:
        pickle.dump(scaler_y, f)
    with open("models/lstm_scaler_x.pkl", "wb") as f:
        pickle.dump(scaler_x, f)
    with open("models/lstm_weather_scaler.pkl", "wb") as f:
        pickle.dump(weather_scaler, f)
    with open("models/lstm_feature_cols.pkl", "wb") as f:
        pickle.dump(feature_cols, f)

    if not args.no_export:
        export_torchscript(
            model,
            input_size,
            args.seq_length,
            args.horizon,
            future_weather_features,
            save_path="models/lstm_scripted.pt",
        )


if __name__ == "__main__":
    main()
