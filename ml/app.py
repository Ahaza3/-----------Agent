"""
电力负荷预测 — Flask 推理微服务

加载 TorchScript 模型，提供 HTTP 推理接口。
Java 后端通过 OkHttp 调用此服务获取预测结果。

端点:
  POST /predict/forecast    输入原始数据(168h) → 特征工程 → 预测 → 返回 24h
  GET  /health               健康检查

用法: python app.py
端口: 5000
"""

import math
import os
import pickle

import numpy as np
from flask import Flask, request, jsonify

app = Flask(__name__)

# ─── 全局状态 ───
model = None
model_type = None
scaler_x = None
scaler_y = None
feature_cols = None
seq_length = 168

# ─── 加载模型和 scaler ───
try:
    import torch

    script_path = "models/lstm_scripted.pt"
    if os.path.exists(script_path):
        model = torch.jit.load(script_path)
        model.eval()
        model_type = "torchscript"
        print(f"TorchScript model loaded: {script_path}")

        meta = torch.load("models/lstm_meta.pt", weights_only=True, map_location="cpu")
        seq_length = meta.get("seq_length", 168)
        print(f"   seq_length={seq_length}")
    else:
        print(f"TorchScript model not found: {script_path}")

    # 加载 scaler 和特征列
    for fname, attr_name in [
        ("lstm_scaler_x.pkl", "scaler_x"),
        ("lstm_scaler.pkl", "scaler_y"),
        ("lstm_feature_cols.pkl", "feature_cols"),
    ]:
        path = f"models/{fname}"
        if os.path.exists(path):
            with open(path, "rb") as f:
                globals()[attr_name] = pickle.load(f)
            print(f"   {attr_name} loaded: {path}")

except Exception as e:
    print(f"Model load failed: {e}")

# 回退: Prophet
if model is None:
    try:
        prophet_path = "models/prophet_model.pkl"
        if os.path.exists(prophet_path):
            with open(prophet_path, "rb") as f:
                model = pickle.load(f)
            model_type = "prophet"
            print(f"Prophet model loaded: {prophet_path}")
    except Exception as e:
        print(f"Prophet model load failed: {e}")

if model is None:
    print("No model available. Run train_lstm.py first.")
else:
    print(f"Inference ready (model={model_type})")


# ─── 特征工程 ───

def _engineer_features(raw_rows: list[dict]) -> np.ndarray:
    """从原始数据行构建 (N, 19) 特征矩阵"""
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
        if col in df.columns and df[col].isna().any():
            df[col] = df[col].bfill().ffill()

    return df[feature_cols].values.astype(np.float32)


# ─── API ───

@app.route("/predict/forecast", methods=["POST"])
def predict_forecast():
    """
    输入原始数据 → 特征工程 → 标准化 → 模型推理 → 返回 24h 预测

    请求体: {"data": [{"time": "...", "load_mw": ..., "temperature": ...}, ...]}
    响应:   {"predictions": [820.5, ...], "model": "lstm"}
    """
    data = request.json
    if not data or "data" not in data:
        return jsonify({"error": "missing 'data' field"}), 400

    raw_rows = data["data"]
    future_weather = data.get("future_weather", [])
    if len(raw_rows) < seq_length:
        return jsonify({"error": f"need >= {seq_length} rows, got {len(raw_rows)}"}), 400

    if model is None:
        return jsonify({"predictions": [1000.0] * 24, "model": "placeholder"})

    try:
        features = _engineer_features(raw_rows)
        features = features[-seq_length:]

        if scaler_x is not None:
            features = scaler_x.transform(features)

        tensor = torch.from_numpy(features).unsqueeze(0)

        with torch.no_grad():
            preds_scaled = model(tensor).squeeze().numpy()

        if scaler_y is not None:
            predictions = scaler_y.inverse_transform(
                preds_scaled.reshape(-1, 1)
            ).flatten().tolist()
        else:
            predictions = preds_scaled.tolist()

    except Exception as e:
        return jsonify({"error": f"inference failed: {e}"}), 500

    predictions = [round(p, 1) for p in predictions[:24]]
    return jsonify({
        "predictions": predictions,
        "model": model_type,
        "future_weather_received": len(future_weather),
    })


@app.route("/health")
def health():
    return jsonify({
        "status": "ok",
        "model_loaded": model is not None,
        "model_type": model_type,
    })


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
