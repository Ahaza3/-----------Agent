"""负荷预测 Flask 推理服务。"""

import math
import os
import pickle

import numpy as np
from flask import Flask, jsonify, request

app = Flask(__name__)

model = None
model_type = None
scaler_x = None
scaler_y = None
weather_scaler = None
feature_cols = None
seq_length = 168
forecast_horizon = 24
future_weather_features = 0
torch = None


try:
    import torch as _torch

    torch = _torch
    script_path = "models/lstm_scripted.pt"
    if os.path.exists(script_path):
        model = torch.jit.load(script_path, map_location="cpu")
        model.eval()
        model_type = "torchscript"
        print(f"TorchScript 模型已加载: {script_path}")

        meta = torch.load(
            "models/lstm_meta.pt", weights_only=True, map_location="cpu"
        )
        seq_length = int(meta.get("seq_length", 168))
        forecast_horizon = int(meta.get("forecast_horizon", 24))
        future_weather_features = int(meta.get("future_weather_features", 0))
        print(
            f"历史窗口={seq_length}，预测时长={forecast_horizon}，"
            f"未来天气特征数={future_weather_features}"
        )
    else:
        print(f"未找到 TorchScript 模型: {script_path}")

    for fname, attr_name in [
        ("lstm_scaler_x.pkl", "scaler_x"),
        ("lstm_scaler.pkl", "scaler_y"),
        ("lstm_weather_scaler.pkl", "weather_scaler"),
        ("lstm_feature_cols.pkl", "feature_cols"),
    ]:
        path = f"models/{fname}"
        if os.path.exists(path):
            with open(path, "rb") as f:
                globals()[attr_name] = pickle.load(f)
            print(f"推理文件已加载: {path}")
except Exception as e:
    print(f"模型加载失败: {e}")


if model is None:
    try:
        prophet_path = "models/prophet_model.pkl"
        if os.path.exists(prophet_path):
            with open(prophet_path, "rb") as f:
                model = pickle.load(f)
            model_type = "prophet"
            print(f"Prophet 模型已加载: {prophet_path}")
    except Exception as e:
        print(f"Prophet 模型加载失败: {e}")

if model is None:
    print("当前没有可用模型，请先运行 train_lstm.py。")
else:
    print(f"推理服务已就绪，当前模型: {model_type}")


def _engineer_features(raw_rows: list[dict]) -> np.ndarray:
    import pandas as pd

    df = pd.DataFrame(raw_rows)
    df["time"] = pd.to_datetime(df["time"])
    df = df.sort_values("time").reset_index(drop=True)

    df["hour_sin"] = np.sin(2 * math.pi * df["hour"] / 24)
    df["hour_cos"] = np.cos(2 * math.pi * df["hour"] / 24)
    df["dow_sin"] = np.sin(2 * math.pi * df["day_of_week"] / 7)
    df["dow_cos"] = np.cos(2 * math.pi * df["day_of_week"] / 7)
    df["month_sin"] = np.sin(2 * math.pi * df["month"] / 12)
    df["month_cos"] = np.cos(2 * math.pi * df["month"] / 12)

    df["load_lag_1"] = df["load_mw"].shift(1)
    df["load_lag_24"] = df["load_mw"].shift(24)
    df["load_lag_168"] = df["load_mw"].shift(168)
    df["temp_lag_1"] = df["temperature"].shift(1)
    df["temp_lag_24"] = df["temperature"].shift(24)
    df["load_roll_mean_24"] = df["load_mw"].rolling(24, min_periods=1).mean()
    df["load_roll_std_24"] = df["load_mw"].rolling(24, min_periods=1).std().fillna(0)

    for col in feature_cols:
        if col not in df.columns:
            df[col] = 0
        df[col] = df[col].bfill().ffill().fillna(0)

    return df[feature_cols].values.astype(np.float32)


def _number(value):
    try:
        number = float(value)
        return number if math.isfinite(number) else None
    except (TypeError, ValueError):
        return None


def _future_weather_tensor(raw_rows: list[dict], rows: list[dict]):
    """构建标准化天气张量，并返回是否使用回退数据。"""
    last_row = raw_rows[-1] if raw_rows else {}
    fallback_values = np.array(
        [
            _number(last_row.get("temperature")) or 0.0,
            _number(last_row.get("humidity")) or 0.0,
        ],
        dtype=np.float32,
    )

    values = np.zeros((forecast_horizon, future_weather_features), dtype=np.float32)
    used_fallback = len(rows) < forecast_horizon
    has_real_weather = False
    for index in range(forecast_horizon):
        source = rows[index] if index < len(rows) else {}
        temperature = _number(source.get("temperature"))
        humidity = _number(source.get("humidity"))
        if temperature is None or humidity is None:
            used_fallback = True
            values[index] = fallback_values
        else:
            has_real_weather = True
            values[index] = [temperature, humidity]

    if weather_scaler is None:
        scaled = np.zeros_like(values)
        used_fallback = True
    else:
        scaled = weather_scaler.transform(values).astype(np.float32)

    tensor = torch.from_numpy(scaled).unsqueeze(0)
    applied = bool(has_real_weather and weather_scaler is not None)
    return tensor, applied, used_fallback


@app.route("/predict/forecast", methods=["POST"])
def predict_forecast():
    data = request.json
    if not data or "data" not in data:
        return jsonify({"error": "缺少 data 字段"}), 400

    raw_rows = data["data"]
    future_weather = data.get("future_weather", [])
    if len(raw_rows) < seq_length:
        return jsonify({"error": f"历史数据至少需要 {seq_length} 行，实际收到 {len(raw_rows)} 行"}), 400

    if model is None:
        return jsonify(
            {
                "predictions": [1000.0] * forecast_horizon,
                "model": "placeholder",
                "future_weather_received": len(future_weather),
                "future_weather_applied": False,
                "future_weather_fallback": False,
            }
        )

    try:
        features = _engineer_features(raw_rows)[-seq_length:]
        if scaler_x is not None:
            features = scaler_x.transform(features)
        tensor = torch.from_numpy(features.astype(np.float32)).unsqueeze(0)

        weather_applied = False
        weather_fallback = False
        if model_type == "torchscript" and future_weather_features > 0:
            weather_tensor, weather_applied, weather_fallback = _future_weather_tensor(
                raw_rows, future_weather
            )
            with torch.no_grad():
                preds_scaled = model(tensor, weather_tensor).squeeze().numpy()
        else:
            with torch.no_grad():
                preds_scaled = model(tensor).squeeze().numpy()

        if scaler_y is not None:
            predictions = scaler_y.inverse_transform(
                preds_scaled.reshape(-1, 1)
            ).flatten().tolist()
        else:
            predictions = preds_scaled.tolist()
    except Exception as e:
        return jsonify({"error": f"模型推理失败: {e}"}), 500

    predictions = [round(float(p), 1) for p in predictions[:forecast_horizon]]
    return jsonify(
        {
            "predictions": predictions,
            "model": model_type,
            "future_weather_received": len(future_weather),
            "future_weather_applied": weather_applied,
            "future_weather_fallback": weather_fallback,
        }
    )


@app.route("/health")
def health():
    return jsonify(
        {
            "status": "ok",
            "model_loaded": model is not None,
            "model_type": model_type,
            "future_weather_supported": future_weather_features > 0,
            "future_weather_features": future_weather_features,
            "forecast_horizon": forecast_horizon,
        }
    )


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
