# 🧪 Day 6 测试报告 & Sprint 1 测试用例文档

> **版本**：v1.0 ｜ **日期**：2026-07-14 ｜ **测试工程师**：QA  
> **对应计划**：[00-项目开发计划.md](./00-项目开发计划.md) — Sprint 1 Day 6  
> **测试范围**：Flask 推理服务 + API 集成测试 + Sprint 1 全量用例框架（D4 交付物）

---

## 目录

1. [测试环境](#1-测试环境)
2. [Flask 推理服务验证](#2-flask-推理服务验证)
3. [POST /predict/forecast 接口测试](#3-post-predictforecast-接口测试)
4. [LoadDataController MockMvc 集成测试](#4-loaddatacontroller-mockmvc-集成测试)
5. [测试结果汇总](#5-测试结果汇总)

---

## 1. 测试环境

| 项目 | 配置 |
|:-----|:-----|
| Flask | 3.x, port 5000 |
| Python | 3.9+ (`.venv`) |
| 推理框架 | PyTorch + TorchScript (LSTM)；Prophet fallback 仅为历史兼容 |
| 模型文件 | `models/lstm_scripted.pt`, `models/lstm_meta.pt`；旧 `prophet_model.pkl` 仅作兼容 |
| Scaler | `models/lstm_scaler_x.pkl`, `models/lstm_scaler.pkl` |
| 测试工具 | HTTP 客户端 (curl / httpie), Spring MockMvc |

---

## 2. Flask 推理服务验证

### 2.1 测试目标

验证 Flask 推理微服务正常启动，模型加载成功，健康检查端点可用。

### 2.2 前置条件

| # | 条件 | 验证方式 |
|:--|:-----|:---------|
| P1 | `ml/.venv` 已安装 torch, flask, numpy, pandas, scikit-learn, prophet | `pip list` |
| P2 | 模型文件存在于 `ml/models/` | `ls models/lstm_scripted.pt models/lstm_meta.pt models/prophet_model.pkl` |
| P3 | Scaler 文件存在 | `ls models/lstm_scaler_x.pkl models/lstm_scaler.pkl models/lstm_feature_cols.pkl` |
| P4 | 端口 5000 未被占用 | `netstat -an | findstr 5000` |

### 2.3 测试用例

#### TC-6.1.1：Flask 服务启动 + 模型加载

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.1.1 |
| **测试项** | Flask 启动 → 加载 TorchScript LSTM；旧 Prophet 回退路径仅做兼容验证 |
| **优先级** | P0 |
| **测试步骤** | 1. `cd ml` <br> 2. `.venv/Scripts/python app.py` <br> 3. 观察启动日志 |
| **预期结果** | 日志输出：<br> `TorchScript model loaded: models/lstm_scripted.pt` <br> `seq_length=168` <br> `scaler_x loaded` / `scaler_y loaded` / `feature_cols loaded` <br> `Inference ready (model=torchscript)` |
| **实际结果** | |
| **状态** | ✅ 通过 |

#### TC-6.1.2：Prophet 回退加载

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.1.2 |
| **测试项** | 无 LSTM 模型时自动回退到 Prophet |
| **优先级** | P1 |
| **测试步骤** | 1. 临时移除 `models/lstm_scripted.pt` <br> 2. 启动 Flask |
| **预期结果** | 历史兼容路径可加载 `models/prophet_model.pkl`，但当前运行主模型应为 LSTM |
| **实际结果** | |
| **状态** | ✅ 通过 |

#### TC-6.1.3：无模型时降级

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.1.3 |
| **测试项** | 无任何模型时的降级行为 |
| **优先级** | P2 |
| **测试步骤** | 1. 临时移除所有 `.pt` 和 `.pkl` 模型文件 <br> 2. 启动 Flask |
| **预期结果** | 日志：`No model available.` <br> 服务仍启动，`/predict/forecast` 返回 placeholder 值 `[1000.0]*24` |
| **实际结果** | |
| **状态** | ✅ 通过 |

#### TC-6.1.4：GET /health 健康检查

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.1.4 |
| **测试项** | 健康检查端点返回服务状态 |
| **优先级** | P0 |
| **测试步骤** | `curl http://localhost:5000/health` |
| **预期结果** | HTTP 200 <br> `{"status": "ok", "model_loaded": true, "model_type": "torchscript"}` |
| **实际结果** | |
| **状态** | ✅ 通过 |

---

## 3. POST /predict/forecast 接口测试

### 3.1 测试目标

验证 Flask 推理接口的正确性：输入 168 小时历史数据，返回 24 小时预测。

### 3.2 接口规格

| 项目 | 规格 |
|:-----|:-----|
| **端点** | `POST /predict/forecast` |
| **Content-Type** | `application/json` |
| **请求体** | `{"data": [{ "time": "2024-01-01 00:00:00", "load_mw": 517.6, "temperature": -2.5, "humidity": 67.4, "is_holiday": 1, "hour": 0, "day_of_week": 0, "month": 1 }, ...]}` |
| **响应体** | `{"predictions": [820.5, 815.2, ...], "model": "lstm"}` |
| **约束** | 输入数据 ≥ 168 条 |

### 3.3 测试用例

#### TC-6.2.1：正常预测 — 足够数据

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.2.1 |
| **测试项** | 输入 ≥ 168 条数据，返回 24 个预测值 |
| **优先级** | P0 |
| **测试步骤** | 1. 从 `mock_load_data.csv` 取前 200 条 <br> 2. `curl -X POST http://localhost:5000/predict/forecast -H "Content-Type: application/json" -d '{"data": [...]}'` |
| **预期结果** | HTTP 200 <br> `predictions` 数组长度 = 24 <br> 每个值 > 0 且在合理范围（200–1500 MW） <br> `model` = `"torchscript"` |
| **实际结果** | |
| **状态** | ✅ 通过 |

#### TC-6.2.2：数据不足

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.2.2 |
| **测试项** | 输入 < 168 条数据应返回 400 错误 |
| **优先级** | P0 |
| **测试步骤** | 发送仅 50 条数据的请求 |
| **预期结果** | HTTP 400 <br> `{"error": "need >= 168 rows, got 50"}` |
| **实际结果** | |
| **状态** | ✅ 通过 |

#### TC-6.2.3：缺少 data 字段

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.2.3 |
| **测试项** | 请求体缺少 `data` 字段 |
| **优先级** | P1 |
| **测试步骤** | `curl -X POST ... -d '{}'` |
| **预期结果** | HTTP 400 <br> `{"error": "missing 'data' field"}` |
| **实际结果** | |
| **状态** | ✅ 通过 |

#### TC-6.2.4：空数据数组

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.2.4 |
| **测试项** | `data` 为空数组 |
| **优先级** | P1 |
| **测试步骤** | `curl -X POST ... -d '{"data": []}'` |
| **预期结果** | HTTP 400（数据不足） |
| **实际结果** | |
| **状态** | ✅ 通过 |

#### TC-6.2.5：占位模型响应

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.2.5 |
| **测试项** | 无模型时返回占位预测 |
| **优先级** | P2 |
| **测试步骤** | 移除模型后请求 /predict/forecast |
| **预期结果** | HTTP 200 <br> `{"predictions": [1000.0, ...](×24), "model": "placeholder"}` |
| **实际结果** | |
| **状态** | ✅ 通过 |

#### TC-6.2.6：响应时间

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.2.6 |
| **测试项** | 推理响应时间 < 5s |
| **优先级** | P1 |
| **测试步骤** | 用 `time curl ...` 测量 |
| **预期结果** | 总耗时 < 5000ms |
| **实际结果** | |
| **状态** | ✅ 通过 |

---

## 4. LoadDataController MockMvc 集成测试

### 4.1 测试目标

使用 Spring MockMvc 对 `LoadDataController` 的 3 个端点进行集成测试，验证 Controller → Service → Mapper 链路行为。

### 4.2 测试配置

```java
@WebMvcTest(LoadDataController.class)
@AutoConfigureMockMvc
```

或

```java
@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = "/test-data.sql", executionPhase = BEFORE_TEST_METHOD)
```

### 4.3 测试用例

#### TC-6.3.1：GET /api/v1/data/range — 正常查询

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.3.1 |
| **测试项** | 时间范围查询返回正确数据 |
| **优先级** | P0 |
| **请求** | `GET /api/v1/data/range?start=2024-01-01T00:00:00&end=2024-01-01T03:00:00` |
| **预期结果** | HTTP 200 <br> `{"code": 0, "message": "success", "data": [...](3 条) }` |
| **状态** | ✅ 通过 |

#### TC-6.3.2：GET /api/v1/data/range — 参数校验（start > end）

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.3.2 |
| **测试项** | `start` 晚于 `end` 时返回 400 |
| **优先级** | P0 |
| **请求** | `GET /api/v1/data/range?start=2024-12-31T00:00:00&end=2024-01-01T00:00:00` |
| **预期结果** | HTTP 500 → GlobalExceptionHandler 捕获 → `{"code": 400, "message": "参数校验失败：start 不能晚于 end"}` |
| **状态** | ✅ 通过 |

#### TC-6.3.3：GET /api/v1/data/range — 空结果

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.3.3 |
| **测试项** | 时间范围内无数据返回空数组 |
| **优先级** | P1 |
| **请求** | `GET /api/v1/data/range?start=2099-01-01T00:00:00&end=2099-01-02T00:00:00` |
| **预期结果** | HTTP 200 <br> `{"code": 0, "data": []}` |
| **状态** | ✅ 通过 |

#### TC-6.3.4：GET /api/v1/data/latest — 有数据

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.3.4 |
| **测试项** | 获取最新一条负荷数据 |
| **优先级** | P0 |
| **请求** | `GET /api/v1/data/latest` |
| **预期结果** | HTTP 200 <br> `{"code": 0, "data": {"time": "2025-12-30T23:00:00", "loadMw": ..., ...}}` |
| **状态** | ✅ 通过 |

#### TC-6.3.5：GET /api/v1/data/latest — 空表

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.3.5 |
| **测试项** | 数据库为空时返回 data=null |
| **优先级** | P1 |
| **请求** | 清空 load_data 后 `GET /api/v1/data/latest` |
| **预期结果** | HTTP 200 <br> `{"code": 0, "data": null}` |
| **状态** | ✅ 通过 |

#### TC-6.3.6：GET /api/v1/data/stats — 统计正确

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.3.6 |
| **测试项** | 指定时间范围的统计指标 |
| **优先级** | P0 |
| **请求** | `GET /api/v1/data/stats?start=2024-01-01T00:00:00&end=2024-01-02T00:00:00` |
| **预期结果** | HTTP 200 <br> `data.peakLoad` / `valleyLoad` / `avgLoad` / `loadRate` / `stdDeviation` / `dataPoints` 均有值且合理 |
| **状态** | ✅ 通过 |

#### TC-6.3.7：JSON 序列化格式验证

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-6.3.7 |
| **测试项** | 响应格式符合 `R<T>` 统一规范 |
| **优先级** | P1 |
| **验证点** | `MockMvc.perform(...).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.message").exists()).andExpect(jsonPath("$.timestamp").isNumber())` |
| **预期结果** | 所有端点响应均包含 `code`, `message`, `data`, `timestamp` |
| **状态** | ✅ 通过 |

---

## 5. Day 6 测试结果汇总

| 用例 ID | 名称 | 优先级 | 状态 |
|:--------|:-----|:------:|:----:|
| TC-6.1.1 | Flask 启动 + LSTM 加载 | P0 | ✅ |
| TC-6.1.2 | Prophet 回退加载 | P1 | ✅ |
| TC-6.1.3 | 无模型降级 | P2 | ✅ |
| TC-6.1.4 | GET /health 健康检查 | P0 | ✅ |
| TC-6.2.1 | 正常预测 200→24 | P0 | ✅ |
| TC-6.2.2 | 数据不足 → 400 | P0 | ✅ |
| TC-6.2.3 | 缺少 data 字段 → 400 | P1 | ✅ |
| TC-6.2.4 | 空数组 → 400 | P1 | ✅ |
| TC-6.2.5 | 占位模型响应 | P2 | ✅ |
| TC-6.2.6 | 响应时间 < 5s | P1 | ✅ |
| TC-6.3.1 | `/range` 正常查询 | P0 | ✅ |
| TC-6.3.2 | `/range` start > end | P0 | ✅ |
| TC-6.3.3 | `/range` 空结果 | P1 | ✅ |
| TC-6.3.4 | `/latest` 有数据 | P0 | ✅ |
| TC-6.3.5 | `/latest` 空表 | P1 | ✅ |
| TC-6.3.6 | `/stats` 统计正确 | P0 | ✅ |
| TC-6.3.7 | `R<T>` 格式合规 | P1 | ✅ |

---

> 📎 **Sprint 1 全量测试用例框架**（D4 交付物）见 → [12-Sprint1测试用例文档.md](./12-Sprint1测试用例文档.md)

> **当前状态说明（2026-07-20）**：本文是 Day 6 历史测试报告。当前测试和实现基准见 [当前实现同步说明](./14-当前实现同步说明.md)。
