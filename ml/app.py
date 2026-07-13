"""
电力负荷预测 — Flask 推理微服务

加载 TorchScript 模型，提供 HTTP 推理接口。
Java 后端通过 OkHttp 调用此服务获取预测结果。

模型输入: 过去 seq_length 小时的标准化特征矩阵
模型输出: 未来 24 小时的负荷预测值

用法: python app.py
端口: 5000
"""

import os
import pickle

import numpy as np
from flask import Flask, request, jsonify

app = Flask(__name__)

# ─── 模型加载 ───

model = None
model_type = None         # "torchscript" | "prophet"
scaler_y = None            # StandardScaler, 反标准化 LSTM 输出
input_size = None
seq_length = None

# 1. 尝试加载 LSTM TorchScript 模型
try:
    import torch
    script_path = "models/lstm_scripted.pt"
    if os.path.exists(script_path):
        model = torch.jit.load(script_path)
        model.eval()
        model_type = "torchscript"
        print(f"TorchScript model loaded: {script_path}")

        meta = torch.load("models/lstm_meta.pt", weights_only=True)
        input_size = meta["input_size"]
        seq_length = meta.get("seq_length", 168)
        print(f"   input_size={input_size}, seq_length={seq_length}")

        scaler_path = "models/lstm_scaler.pkl"
        if os.path.exists(scaler_path):
            with open(scaler_path, "rb") as f:
                scaler_y = pickle.load(f)
            print(f"   scaler loaded: {scaler_path}")
    else:
        print(f"TorchScript model not found: {script_path}")
except Exception as e:
    print(f"LSTM model load failed: {e}")

# 2. 回退: Prophet
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
    print("No model available, /predict/batch returns placeholder. Run train_lstm.py first.")
else:
    print(f"Inference ready (model={model_type})")


# ─── API ───

def _validate_lstm_input(features: list) -> np.ndarray:
    """校验 LSTM 输入并截取最后 seq_length 行"""
    arr = np.array(features, dtype=np.float32)
    if arr.ndim != 2:
        raise ValueError(f"features must be 2D, got {arr.ndim}D")
    if arr.shape[1] != input_size:
        raise ValueError(f"feature dim mismatch: expected {input_size}, got {arr.shape[1]}")
    if arr.shape[0] < seq_length:
        raise ValueError(f"not enough rows: need >= {seq_length}, got {arr.shape[0]}")
    return arr[-seq_length:]


@app.route("/predict/batch", methods=["POST"])
def predict_batch():
    """
    Predict next 24h load.

    LSTM:  {"features": [[f1,...,fN], ...]}  (>= 168 rows x input_size cols)
    Prophet: {"ds": ["2024-01-01 00:00:00", ...], "temperature": [...], ...}

    Response: {"predictions": [820.5, 790.2, ...]}  (24 floats)
    """
    data = request.json
    if not data:
        return jsonify({"error": "empty body"}), 400

    if model is None:
        return jsonify({"predictions": [1000.0] * 24})

    try:
        if model_type == "torchscript":
            import torch
            if "features" not in data:
                return jsonify({"error": "missing features"}), 400

            window = _validate_lstm_input(data["features"])
            tensor = torch.from_numpy(window).unsqueeze(0)  # (1, seq_len, features)

            with torch.no_grad():
                preds_scaled = model(tensor).squeeze().numpy()  # (24,)

            if scaler_y is not None:
                predictions = scaler_y.inverse_transform(
                    preds_scaled.reshape(-1, 1)
                ).flatten().tolist()
            else:
                predictions = preds_scaled.tolist()

        elif model_type == "prophet":
            import pandas as pd
            if "ds" not in data:
                return jsonify({"error": "Prophet mode needs ds field"}), 400
            future = pd.DataFrame(data)
            forecast = model.predict(future)
            predictions = forecast["yhat"].values[:24].tolist()
        else:
            predictions = [1000.0] * 24

    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        return jsonify({"error": f"inference failed: {e}"}), 500

    # Ensure exactly 24 values
    while len(predictions) < 24:
        predictions.append(predictions[-1])
    predictions = predictions[:24]

    return jsonify({"predictions": [round(p, 1) for p in predictions]})


@app.route("/health")
def health():
    return jsonify({
        "status": "ok",
        "model_loaded": model is not None,
        "model_type": model_type,
    })


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
