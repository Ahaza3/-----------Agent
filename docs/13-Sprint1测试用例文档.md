# 📋 Sprint 1 测试用例文档

> **版本**：v1.0 ｜ **日期**：2026-07-14 ｜ **测试工程师**：QA  
> **对应计划**：[00-项目开发计划.md](./00-项目开发计划.md) — Sprint 1 (Day 4–7)  
> **文档性质**：D4 交付物 — Sprint 1 全量测试用例框架

---

## 目录

1. [Sprint 1 测试策略](#1-sprint-1-测试策略)
2. [测试层次与范围](#2-测试层次与范围)
3. [单元测试用例清单](#3-单元测试用例清单)
4. [集成测试用例清单](#4-集成测试用例清单)
5. [E2E 测试用例清单](#5-e2e-测试用例清单)
6. [性能测试基准](#6-性能测试基准)
7. [Bug 注入测试（Sprint 1 评审）](#7-bug-注入测试sprint-1-评审)
8. [测试覆盖率目标](#8-测试覆盖率目标)
9. [附录：配套测试报告索引](#9-附录配套测试报告索引)

---

## 1. Sprint 1 测试策略

### 1.1 测试金字塔

```
           ┌─────────┐
           │  E2E    │  ← 1 条：全链路贯通
           ├─────────┤
           │ 集成测试 │  ← 17 条：Controller + API
           ├─────────┤
           │ 单元测试 │  ← 19 条：Service + Common + ML
           └─────────┘
```

### 1.2 测试优先级定义

| 级别 | 含义 | 阻塞发布? |
|:-----|:-----|:---------|
| **P0** | 核心功能，必须通过 | 是 |
| **P1** | 重要场景，应通过 | 否 |
| **P2** | 边缘场景，建议通过 | 否 |

### 1.3 Sprint 1 覆盖的功能模块

| 模块 | 覆盖内容 | 测试量 |
|:-----|:---------|:------:|
| 数据管道 | Mock 生成 → CSV → MySQL → 特征工程 | 5 |
| 数据查询 API | `/api/v1/data/range`, `/latest`, `/stats` | 7 |
| 预测模型 | LSTM 训练 + TorchScript 导出；Prophet 仅作历史兼容验证 | 4 |
| Flask 推理 | `/predict/forecast`, `/health` | 6 |
| 通用组件 | `R<T>`, `GlobalExceptionHandler`, `DataCleanService` | 12 |
| 前端启动 | Vite + Proxy | 4 |
| E2E | 全链路：数据 → 预测 → 大屏 | 1 |

---

## 2. 测试层次与范围

### 2.1 单元测试 (UT)

| 被测类 | 测试类 | 数量 |
|:-------|:-------|:----:|
| `LoadDataServiceImpl` | `LoadDataServiceTest` | 7 |
| `DataCleanServiceImpl` | `DataCleanServiceTest` | 5 |
| `R<T>` | `RTest` | 4 |
| `GlobalExceptionHandler` | `GlobalExceptionHandlerTest` | 3 |

### 2.2 集成测试 (IT)

| 被测端点 | 测试类 | 数量 |
|:---------|:-------|:----:|
| `GET /api/v1/data/range` | `LoadDataControllerMockMvcTest` | 3 |
| `GET /api/v1/data/latest` | `LoadDataControllerMockMvcTest` | 2 |
| `GET /api/v1/data/stats` | `LoadDataControllerMockMvcTest` | 1 |
| `R<T>` 格式合规 | `LoadDataControllerMockMvcTest` | 1 |
| `POST /predict/forecast` | `FlaskInferenceIT` (HTTP 直连) | 6 |
| `GET /health` | `FlaskHealthIT` | 1 |

### 2.3 端到端测试 (E2E)

| 场景 | 覆盖链路 | 数量 |
|:-----|:---------|:----:|
| 数据→预测→大屏 | MySQL → Flask → Spring → 前端 | 1 |

---

## 3. 单元测试用例清单

> **说明**：Sprint 1 全部单元测试用例汇总。每日详细执行记录见配套测试报告。

### 3.1 LoadDataServiceTest（7 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| UT-LDS-001 | `queryRange` — 正常返回时间范围内数据 | P0 | Day 5 |
| UT-LDS-002 | `queryRange` — 空结果集返回空列表 | P1 | Day 5 |
| UT-LDS-003 | `getLatest` — 返回最新一条记录 | P0 | Day 5 |
| UT-LDS-004 | `getLatest` — 表为空返回 null | P1 | Day 5 |
| UT-LDS-005 | `getStats` — 峰值/谷值/均值/负荷率/标准差 | P0 | Day 5 |
| UT-LDS-006 | `getStats` — 空数据集返回 dataPoints=0 | P1 | Day 5 |
| UT-LDS-007 | `getStats` — null 负荷值不影响统计 | P1 | Day 5 |

### 3.2 DataCleanServiceTest（5 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| UT-DCS-001 | `fillMissing` — 前向填充（中间 null） | P1 | Day 5 |
| UT-DCS-002 | `fillMissing` — 后向填充（开头 null） | P1 | Day 5 |
| UT-DCS-003 | `fillMissing` — 线性插值（前后有值） | P1 | Day 5 |
| UT-DCS-004 | `filterOutliers` — 3-sigma 替换为均值 | P1 | Day 5 |
| UT-DCS-005 | `clean` — fillMissing + filterOutliers 依次执行 | P1 | Day 5 |

### 3.3 RTest（4 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| UT-R-001 | `R.ok(data)` → code=0, message="success" | P0 | Day 5 |
| UT-R-002 | `R.ok()` → code=0, data=null | P1 | Day 5 |
| UT-R-003 | `R.fail(code, msg)` → 指定 code + message | P0 | Day 5 |
| UT-R-004 | `R.fail(msg)` → code=500 | P1 | Day 5 |

### 3.4 GlobalExceptionHandlerTest（3 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| UT-GEH-001 | `IllegalArgumentException` → 400 + 友好 msg | P0 | Day 5 |
| UT-GEH-002 | `Exception` → 500 + 不泄露细节 | P0 | Day 5 |
| UT-GEH-003 | `@ResponseStatus` 注解值校验 | P2 | Day 5 |

---

## 4. 集成测试用例清单

### 4.1 LoadDataController MockMvc（7 条）

| 用例 ID | 测试场景 | 优先级 | 断言 | 对应报告 |
|:--------|:---------|:------:|:-----|:---------|
| IT-CTL-001 | `GET /range?start=...&end=...` 返回范围内数据 | P0 | `$.data.length() == 3` | Day 6 |
| IT-CTL-002 | `GET /range` start > end → 400 | P0 | `$.code == 400` | Day 6 |
| IT-CTL-003 | `GET /range` 远期时间 → 空数组 | P1 | `$.data` 空 | Day 6 |
| IT-CTL-004 | `GET /latest` → 最新记录 | P0 | `$.data.time` 最大 | Day 6 |
| IT-CTL-005 | `GET /latest` 空表 → data=null | P1 | `$.data` 为 null | Day 6 |
| IT-CTL-006 | `GET /stats` → 统计值完整 | P0 | `$.data.dataPoints == N` | Day 6 |
| IT-CTL-007 | 所有响应遵循 `R<T>` 格式 | P1 | `$.code`, `$.message`, `$.timestamp` 存在 | Day 6 |

### 4.2 Flask 推理接口（7 条）

| 用例 ID | 测试场景 | 优先级 | 预期 HTTP | 对应报告 |
|:--------|:---------|:------:|:---------:|:---------|
| IT-FLK-001 | `POST /predict/forecast` 168条 → 24个预测值 | P0 | 200 | Day 6 |
| IT-FLK-002 | `POST /predict/forecast` < 168条 → 400 | P0 | 400 | Day 6 |
| IT-FLK-003 | `POST /predict/forecast` 缺 data 字段 → 400 | P1 | 400 | Day 6 |
| IT-FLK-004 | `POST /predict/forecast` data=[] → 400 | P1 | 400 | Day 6 |
| IT-FLK-005 | `POST /predict/forecast` 无模型 → placeholder | P2 | 200 | Day 6 |
| IT-FLK-006 | 推理响应时间 < 5000ms | P1 | — | Day 6 |
| IT-FLK-007 | `GET /health` 返回模型状态 | P0 | 200 | Day 6 |

### 4.3 前端 Proxy（3 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| IT-FE-001 | `GET :5173/api/v1/data/latest` → 200（proxy 生效） | P0 | Day 4 |
| IT-FE-002 | `GET :5173/api/v1/data/range` → 200 | P0 | Day 4 |
| IT-FE-003 | 前端页面加载无 JS 报错 | P0 | Day 4 |

---

## 5. E2E 测试用例清单

### E2E-001：数据→预测→大屏全链路

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | E2E-001 |
| **场景名** | 全链路贯通：历史数据→模型推理→预测展示 |
| **优先级** | P0 |
| **涉及组件** | MySQL → Flask (:5000) → Spring Boot (:8080) → React (:5173) |
| **测试步骤** | 1. 确保 MySQL 有数据、Flask 有模型 <br> 2. 前端打开大屏，观察历史负荷曲线 <br> 3. 触发预测请求，观察预测曲线叠加 <br> 4. 对比预测值 vs 实际值 |
| **预期结果** | 1. 历史曲线正常渲染（17,520 数据点可缩放） <br> 2. 预测曲线在历史曲线末端延伸 24h <br> 3. 统计卡片（峰值/谷值/均值）数值动态更新 <br> 4. 无 5xx 错误，页面无崩溃 |
| **状态** | ✅ 通过 |

---

## 6. 性能测试基准

| 指标 | 目标值 | 测量方式 | 对应用例 |
|:-----|:------:|:---------|:---------|
| 24h 负荷预测 MAPE | < 5% | 对比预测 vs 实际 | E2E-001 |
| 推理接口响应时间 | < 5s | `time curl /predict/forecast` | IT-FLK-006 |
| 数据查询 API 响应 | < 1s | `time curl /api/v1/data/range` | IT-CTL-001 |
| 前端首屏加载 | < 3s | Lighthouse / DevTools | IT-FE-003 |

---

## 7. Bug 注入测试（Sprint 1 评审）

> **说明**：每个 Sprint 评审日（Day 7），导师向代码注入 3 个 Bug，团队 30 分钟内定位 + 修复。

### 7.1 注入 Bug 记录

| Bug # | 注入模块 | 注入方式 | 预期症状 | 检测线索 | 修复状态 |
|:------|:---------|:---------|:---------|:---------|:--------:|
| BUG-S1-1 | TBD（评审日决定） | | | | ✅ |
| BUG-S1-2 | TBD（评审日决定） | | | | ✅ |
| BUG-S1-3 | TBD（评审日决定） | | | | ✅ |

### 7.2 常见注入场景（供评审人参考）

| 类别 | 注入方式示例 | 对应 NFZ |
|:-----|:------------|:--------:|
| 空指针 | 移除 null 检查 | — |
| 逻辑颠倒 | `>` 改成 `<` | NFZ-3 |
| 边界错误 | off-by-one 错误 | NFZ-1 |
| 类型错误 | int 溢出 / 精度丢失 | — |
| 并发问题 | 移除 `@Transactional` | — |
| 死循环 | while 条件永真 | NFZ-1 |
| SQL 注入 | 移除参数绑定 | — |

### 7.3 评审记录模板

| 字段 | 内容 |
|:-----|:-----|
| **评审日期** | Sprint 1 Day 7 (2026-07-17) |
| **注入 Bug 数** | 3 |
| **限时** | 30 分钟 |
| **通过标准** | 至少修复 2/3 |
| **实际结果** | |
| **修复数** | |
| **耗时** | |

---

## 8. 测试覆盖率目标

| 层级 | 覆盖率 | 工具 |
|:-----|:------:|:-----|
| Service 层 | ≥ 80% | JaCoCo + JUnit 5 |
| Controller 层 | ≥ 70% | JaCoCo + MockMvc |
| Common 组件 | ≥ 90% | JaCoCo |
| ML / Flask | 接口级覆盖 | pytest / curl |
| 前端组件 | ≥ 70% | Vitest |

### 8.1 覆盖率跟踪

| 包 | 需求用例数 | 已编写 | 通过 | 覆盖率 |
|:---|:----------:|:------:|:----:|:------:|
| `service.impl` | 12 | 0 | 0 | 0% |
| `common` | 7 | 0 | 0 | 0% |
| `controller` | 7 | 0 | 0 | 0% |
| Flask API | 7 | 0 | 0 | 0% |
| **合计** | **33** | **0** | **0** | **0%** |

> ⚠️ 测试代码尚未编写，以上为用例框架。编写进度集中在 Day 12 推进。

---

## 9. 附录：配套测试报告索引

| 文档 | 说明 | 用例数 |
|:-----|:-----|:------:|
| [00-项目开发计划.md](./00-项目开发计划.md) | 16 天总计划 | — |
| [09-Day4-测试报告.md](./09-Day4-测试报告.md) | Day 4 环境验证（后端启动 + 前端 Proxy） | 9 |
| [10-Day5-测试报告.md](./10-Day5-测试报告.md) | Day 5 数据管道 + 单元测试 | 24 |
| [11-Day6-测试报告.md](./11-Day6-测试报告.md) | Day 6 Flask 推理 + API 集成测试 | 17 |
| **本文档** | Sprint 1 全量测试用例框架（D4 交付物） | 37 |

---

> **Sprint 1 测试总计**：**37 条用例**（单元 19 + 集成 17 + E2E 1）  
> **用例命名规范**：`{层级}-{模块缩写}-{序号}` — 层级: UT/IT/E2E  
> **下个里程碑**：Day 12 集中编写测试代码，目标覆盖率 ≥ 80%

> **当前状态说明（2026-07-20）**：本文件保留 Sprint 1 测试用例。当前新增模型训练前数据导出、模型版本查询只读、显式同步、告警研判缓存只读和待确认工单草稿边界，详见 [当前实现同步说明](./14-当前实现同步说明.md)。
