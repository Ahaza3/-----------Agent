#!/bin/bash
# 电力负荷预测 — 一键环境初始化（新成员 pull 后执行一次）
# 用法: cd ml && bash setup.sh

set -e
echo "=========================================="
echo "  电力负荷预测 Agent — 环境初始化"
echo "=========================================="

# ── 1. Python 虚拟环境 ──
if [ ! -d ".venv" ]; then
    echo "[1/5] 创建 Python 虚拟环境..."
    python3 -m venv .venv
    .venv/Scripts/pip install -r requirements.txt -q
    .venv/Scripts/pip install mysql-connector-python -q
    echo "  ✅ venv 就绪"
else
    echo "[1/5] venv 已存在，跳过"
fi

# ── 2. 生成模拟数据 ──
echo "[2/5] 生成模拟数据 (2年逐小时负荷)..."
PYTHONIOENCODING=utf-8 .venv/Scripts/python generate_mock_data.py
echo "  ✅ mock_load_data.csv"

# ── 3. 导入 MySQL ──
echo "[3/5] 导入 MySQL (power_load.load_data)..."
.venv/Scripts/python -c "
import csv, mysql.connector
conn = mysql.connector.connect(host='localhost', user='root', password='123456', database='power_load')
c = conn.cursor()
c.execute('TRUNCATE TABLE load_data')
with open('mock_load_data.csv', encoding='utf-8') as f:
    rows = [(r['time'], r['load_mw'], r['temperature'], r['humidity'],
             r['is_holiday'], r['hour'], r['day_of_week'], r['month']) for r in csv.DictReader(f)]
c.executemany('INSERT INTO load_data (time, load_mw, temperature, humidity, is_holiday, hour, day_of_week, month) VALUES (%s,%s,%s,%s,%s,%s,%s,%s)', rows)
conn.commit()
c.execute('SELECT COUNT(*) FROM load_data')
print(f'  ✅ {c.fetchone()[0]} 条数据已导入')
c.close(); conn.close()
"

# ── 4. 特征工程 ──
echo "[4/5] 特征工程..."
PYTHONIOENCODING=utf-8 .venv/Scripts/python feature_engineering.py
echo "  ✅ featured_load_data.csv"

# ── 5. 模型训练 ──
echo "[5/5] 模型训练 (可能需要几分钟)..."
PYTHONIOENCODING=utf-8 .venv/Scripts/python train_prophet.py
PYTHONIOENCODING=utf-8 .venv/Scripts/python train_lstm.py

# ── 保存推理所需文件 ──
echo "保存推理 scaler..."
.venv/Scripts/python -c "
import pandas as pd, pickle
from sklearn.preprocessing import StandardScaler
df = pd.read_csv('featured_load_data.csv')
exclude = {'time', 'load_mw', 'created_at'}
feat_cols = [c for c in df.select_dtypes(include=['number']).columns if c not in exclude]
scaler_x = StandardScaler()
scaler_x.fit(df[feat_cols].values.astype('float32'))
with open('models/lstm_scaler_x.pkl', 'wb') as f: pickle.dump(scaler_x, f)
with open('models/lstm_feature_cols.pkl', 'wb') as f: pickle.dump(feat_cols, f)
print(f'  ✅ scaler_x ({len(feat_cols)} features)')
"

echo ""
echo "=========================================="
echo "  ✅ 初始化完成！"
echo ""
echo "  启动服务:"
echo "    cd ml && .venv/Scripts/python app.py       # Flask :5000"
echo "    cd backend && mvn spring-boot:run            # Spring :8080"
echo "    cd frontend && npm run dev                   # Vite :5173"
echo "=========================================="
