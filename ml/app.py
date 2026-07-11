"""
电力负荷预测 — Flask 推理微服务

加载训练好的 PyTorch 模型，提供 HTTP 推理接口。
Java 后端通过 OkHttp 调用此服务获取预测结果。

用法: python app.py
端口: 5000
"""

from flask import Flask, request, jsonify

app = Flask(__name__)

# 启动时加载模型（首次运行时 model 文件不存在则跳过）
model = None
try:
    import torch
    model = torch.jit.load("models/lstm_model.pt")
    model.eval()
    print("✅ 模型加载成功: models/lstm_model.pt")
except FileNotFoundError:
    print("⚠️ 模型文件不存在，/predict/batch 将返回占位数据。请先运行 train_lstm.py")
except Exception as e:
    print(f"⚠️ 模型加载失败: {e}")


@app.route("/predict/batch", methods=["POST"])
def predict_batch():
    """
    批量预测未来 24 小时负荷

    请求体: {"features": [[f1, f2, ...], ...]}  (24 × N 特征矩阵)
    响应:   {"predictions": [820.5, 790.2, ...]}  (24 个 float)
    """
    import numpy as np

    data = request.json
    if not data or "features" not in data:
        return jsonify({"error": "缺少 features 字段"}), 400

    if model is not None:
        import torch
        features = np.array(data["features"], dtype=np.float32)
        tensor = torch.from_numpy(features).unsqueeze(0)
        with torch.no_grad():
            predictions = model(tensor).squeeze().tolist()
        if isinstance(predictions, float):
            predictions = [predictions]
    else:
        # 模型未就绪时返回占位数据（仅开发阶段）
        predictions = [1000.0] * 24

    return jsonify({"predictions": predictions})


@app.route("/health")
def health():
    return jsonify({"status": "ok", "model_loaded": model is not None})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
