# 🧪 Sprint 2 Day 9 测试报告 — 告警检测引擎 + WebSocket

> **版本**：v1.0 ｜ **日期**：2026-07-19 ｜ **测试工程师**：QA  
> **对应计划**：[00-项目开发计划.md](./00-项目开发计划.md) — Sprint 2 Day 9  
> **测试范围**：告警分级检测 + 文案模板 + WebSocket 实时推送 + 告警事件接口

---

## 目录

1. [测试环境](#1-测试环境)
2. [测试用例：ThresholdDetector 告警检测引擎](#2-测试用例thresholddetector-告警检测引擎)
3. [测试用例：AlertTemplate 告警文案模板](#3-测试用例alerttemplate-告警文案模板)
4. [测试用例：AlertJudgementService LLM 研判](#4-测试用例alertjudgementservice-llm-研判)
5. [测试用例：WebSocket 实时推送](#5-测试用例websocket-实时推送)
6. [测试用例：AlertEventController 告警接口](#6-测试用例alerteventcontroller-告警接口)
7. [测试用例：AlertRuleController 规则接口](#7-测试用例alertrulecontroller-规则接口)
8. [测试结果汇总](#8-测试结果汇总)

---

## 1. 测试环境

| 项目 | 配置 |
|:-----|:-----|
| 测试框架 | JUnit 5 + Mockito 5 |
| 断言库 | AssertJ |
| WebSocket 测试 | Spring WebSocket Test |
| MockMvc | Spring Boot Test |
| 被测 NFZ | NFZ-3 (ThresholdDetector + AlertTemplate) |

---

## 2. 测试用例：ThresholdDetector 告警检测引擎

### 2.1 测试目标

验证 `ThresholdDetector` 三级告警判定逻辑、配置解析、冷却时间和边界处理。

### 2.2 被测类

`com.powerload.alert.ThresholdDetector` — NFZ-3 禁飞区，纯数学计算，不涉及 LLM。

### 2.3 测试用例

#### TC-S2D9-001：RED 告警 — 当前负荷超过安全上限 10%

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-001 |
| **测试项** | 负荷超过 threshold × redRatio 时返回 RED |
| **优先级** | P0 |
| **测试步骤** | 配置 threshold=1000, redRatio=1.10, currentLoad=1150 |
| **预期结果** | `detect()` 返回 `AlertLevel.RED` |
| **状态** | ✅ 通过 |

#### TC-S2D9-002：ORANGE 告警 — 当前负荷触及安全上限

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-002 |
| **测试项** | 负荷超过 threshold × orangeRatio 但未达 RED 时返回 ORANGE |
| **优先级** | P0 |
| **测试步骤** | 配置 threshold=1000, orangeRatio=1.00, currentLoad=1050 |
| **预期结果** | `detect()` 返回 `AlertLevel.ORANGE` |
| **状态** | ✅ 通过 |

#### TC-S2D9-003：YELLOW 告警 — 当前负荷接近安全上限

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-003 |
| **测试项** | 负荷超过 threshold × yellowRatio 但未达 ORANGE 时返回 YELLOW |
| **优先级** | P0 |
| **测试步骤** | 配置 threshold=1000, yellowRatio=0.90, currentLoad=920 |
| **预期结果** | `detect()` 返回 `AlertLevel.YELLOW` |
| **状态** | ✅ 通过 |

#### TC-S2D9-004：安全 — 负荷未触及阈值

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-004 |
| **测试项** | 负荷低于 yellowRatio × threshold 时返回 NONE |
| **优先级** | P0 |
| **测试步骤** | 配置 threshold=1000, yellowRatio=0.90, currentLoad=800 |
| **预期结果** | `detect()` 返回 `AlertLevel.NONE` 或 null |
| **状态** | ✅ 通过 |

#### TC-S2D9-005：配置解析失败 — 返回 null

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-005 |
| **测试项** | config JSON 格式错误时不误报 |
| **优先级** | P1 |
| **测试步骤** | 传入非法 JSON 字符串 `"{invalid}"` |
| **预期结果** | `detect()` 返回 null（不抛异常，不误报） |
| **状态** | ✅ 通过 |

#### TC-S2D9-006：threshold ≤ 0 — 不做判定

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-006 |
| **测试项** | threshold 无效时不触发告警 |
| **优先级** | P1 |
| **测试步骤** | 配置 threshold=0, currentLoad=500 |
| **预期结果** | `detect()` 返回 null |
| **状态** | ✅ 通过 |

#### TC-S2D9-007：冷却时间读取

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-007 |
| **测试项** | `getCoolingTime()` 从 config 读取冷却时间 |
| **优先级** | P1 |
| **测试步骤** | 验证 config 中 `coolingTime: 1800` 可正确解析为 1800 秒 |
| **预期结果** | `getCoolingTime()` 返回 1800 |
| **状态** | ✅ 通过 |

#### TC-S2D9-008：默认冷却时间

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-008 |
| **测试项** | config 未配置 coolingTime 时使用默认值 3600s |
| **优先级** | P1 |
| **测试步骤** | 配置不包含 coolingTime 字段 |
| **预期结果** | `getCoolingTime()` 返回 3600 |
| **状态** | ✅ 通过 |

---

## 3. 测试用例：AlertTemplate 告警文案模板

### 3.1 测试目标

验证 `AlertTemplate` 生成的中文告警文案正确、各级别措辞递进合理。

### 3.2 被测类

`com.powerload.alert.AlertTemplate` — NFZ-3 禁飞区，固定字符串模板。

### 3.3 测试用例

#### TC-S2D9-009：RED 级别 — aiAnalysis 文案

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-009 |
| **测试项** | RED 告警生成正确的 aiAnalysis 文案 |
| **优先级** | P0 |
| **测试步骤** | `AlertTemplate.generateAnalysis(AlertLevel.RED, 1150, 1000)` |
| **预期结果** | 文案包含 "超出安全上限"、"1150"、"调峰预案" 关键字 |
| **状态** | ✅ 通过 |

#### TC-S2D9-010：ORANGE 级别 — aiAnalysis 文案

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-010 |
| **测试项** | ORANGE 告警生成正确的 aiAnalysis 文案 |
| **优先级** | P0 |
| **测试步骤** | `AlertTemplate.generateAnalysis(AlertLevel.ORANGE, 1050, 1000)` |
| **预期结果** | 文案包含 "已达到安全上限"、"密切关注" 关键字 |
| **状态** | ✅ 通过 |

#### TC-S2D9-011：YELLOW 级别 — aiAnalysis 文案

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-011 |
| **测试项** | YELLOW 告警生成正确的 aiAnalysis 文案 |
| **优先级** | P0 |
| **测试步骤** | `AlertTemplate.generateAnalysis(AlertLevel.YELLOW, 920, 1000)` |
| **预期结果** | 文案包含 "接近安全上限"、"加强监控" 关键字 |
| **状态** | ✅ 通过 |

#### TC-S2D9-012：RED 级别 — suggestion 措施

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-012 |
| **测试项** | RED 告警建议措施递进正确 |
| **优先级** | P1 |
| **测试步骤** | `AlertTemplate.generateSuggestion(AlertLevel.RED)` |
| **预期结果** | 返回 3 条措施，包含"立即"、"调度长" 等紧急措辞 |
| **状态** | ✅ 通过 |

#### TC-S2D9-013：YELLOW → RED 文案递进

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-013 |
| **测试项** | 三级建议措施存在递进关系 |
| **优先级** | P1 |
| **测试步骤** | 对比三个级别的 suggestion |
| **预期结果** | RED="立即执行"，ORANGE="准备执行"，YELLOW="加强关注" — 递进明显 |
| **状态** | ✅ 通过 |

---

## 4. 测试用例：AlertJudgementService LLM 研判

### 4.1 测试目标

验证告警详情页 AI 研判功能（不参与告警触发延迟指标），以及 LLM 失败时的规则降级。

### 4.2 被测类

`com.powerload.alert.AlertJudgementService`

### 4.3 测试用例

#### TC-S2D9-014：LLM 研判 — 正常返回

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-014 |
| **测试项** | LLM 正常时返回 AI 研判结果 |
| **优先级** | P1 |
| **测试步骤** | Mock LlmClient 返回正常研判文本，调用 `judge(alertId)` |
| **预期结果** | 返回 `AlertJudgementResult` 含分析文本，`fromCache=false` |
| **状态** | ✅ 通过 |

#### TC-S2D9-015：规则降级 — LLM 失败

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-015 |
| **测试项** | LLM 调用失败时降级为规则研判 |
| **优先级** | P1 |
| **测试步骤** | Mock LlmClient 抛出异常，调用 `judge(alertId)` |
| **预期结果** | 返回 `judgeRuleBased()` 的结果，不抛异常，不阻断调用链 |
| **状态** | ✅ 通过 |

#### TC-S2D9-016：GET 缓存读取

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-016 |
| **测试项** | GET 接口仅读取已有缓存，不触发 LLM |
| **优先级** | P1 |
| **测试步骤** | 已有缓存 → `GET /api/v1/alert/{id}/judgement` |
| **预期结果** | 返回缓存结果，LlmClient 未被调用 |
| **状态** | ✅ 通过 |

#### TC-S2D9-017：POST 重新研判

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-017 |
| **测试项** | POST 显式触发重新 LLM 研判 |
| **优先级** | P1 |
| **测试步骤** | `POST /api/v1/alert/{id}/judgement` |
| **预期结果** | 重新调用 LLM，结果更新，`fromCache=false` |
| **状态** | ✅ 通过 |

---

## 5. 测试用例：WebSocket 实时推送

### 5.1 测试目标

验证 WebSocket STOMP 端点 `/ws/dashboard` 正确推送负荷数据、告警事件和预测结果。

### 5.2 被测组件

`com.powerload.websocket.WebSocketConfig` + `PushService`

### 5.3 测试用例

#### TC-S2D9-018：WebSocket 连接建立

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-018 |
| **测试项** | 客户端可正常连接 STOMP 端点 |
| **优先级** | P0 |
| **测试步骤** | SockJS 连接 `http://localhost:8080/ws/dashboard` → STOMP 订阅 |
| **预期结果** | 连接成功，返回 101 升级，STOMP CONNECTED 帧 |
| **状态** | ✅ 通过 |

#### TC-S2D9-019：/topic/load 实时负荷推送

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-019 |
| **测试项** | 后端数据更新时向 `/topic/load` 推送 |
| **优先级** | P0 |
| **测试步骤** | 1. 订阅 `/topic/load` <br> 2. 后端写入新 load_data <br> 3. 验证客户端收到消息 |
| **预期结果** | 客户端收到 JSON 格式负荷数据，含 `loadMw`, `time` |
| **状态** | ✅ 通过 |

#### TC-S2D9-020：/topic/alerts 告警推送

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-020 |
| **测试项** | 告警触发时向 `/topic/alerts` 推送 |
| **优先级** | P0 |
| **测试步骤** | 1. 订阅 `/topic/alerts` <br> 2. 触发阈值告警 <br> 3. 验证客户端收到消息 |
| **预期结果** | 收到告警 JSON，含 `level`, `currentValue`, `thresholdValue`, `aiAnalysis` |
| **状态** | ✅ 通过 |

#### TC-S2D9-021：/topic/predictions 预测推送

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-021 |
| **测试项** | 预测完成后向 `/topic/predictions` 推送 |
| **优先级** | P1 |
| **测试步骤** | 1. 订阅 `/topic/predictions` <br> 2. 触发预测请求 <br> 3. 验证客户端收到消息 |
| **预期结果** | 收到预测结果 JSON，含 24 个预测值数组 |
| **状态** | ✅ 通过 |

---

## 6. 测试用例：AlertEventController 告警接口

### 6.1 测试目标

验证告警事件 CRUD 接口及分页查询。

### 6.2 被测类

`com.powerload.controller.AlertEventController`

### 6.3 测试用例

#### TC-S2D9-022：GET /api/v1/alert/list — 分页查询

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-022 |
| **测试项** | 分页查询告警事件列表 |
| **优先级** | P0 |
| **请求** | `GET /api/v1/alert/list?page=1&size=10` |
| **预期结果** | 返回分页数据，含 `records`, `total`, `current`, `size` |
| **状态** | ✅ 通过 |

#### TC-S2D9-023：GET /api/v1/alert/list — 按级别过滤

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-023 |
| **测试项** | 按告警级别过滤 |
| **优先级** | P1 |
| **请求** | `GET /api/v1/alert/list?level=RED` |
| **预期结果** | 仅返回 level=RED 的告警 |
| **状态** | ✅ 通过 |

#### TC-S2D9-024：PUT /api/v1/alert/{id}/read — 标记已读

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-024 |
| **测试项** | 标记告警为已读状态 |
| **优先级** | P1 |
| **请求** | `PUT /api/v1/alert/{id}/read` |
| **预期结果** | 告警 `is_read` 变为 1 |
| **状态** | ✅ 通过 |

#### TC-S2D9-025：POST /api/v1/alerts/{id}/ticket — 红色告警建单

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-025 |
| **测试项** | 调度员确认后创建正式工单 |
| **优先级** | P0 |
| **测试步骤** | 对 RED 级别告警 POST 建单请求 |
| **预期结果** | 返回工单信息，状态为已确认 |
| **状态** | ✅ 通过 |

---

## 7. 测试用例：AlertRuleController 规则接口

### 7.1 测试目标

验证告警规则的 CRUD 和启用/禁用切换。

### 7.2 测试用例

#### TC-S2D9-026：GET /api/v1/alert/rule/list — 规则列表

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-026 |
| **测试项** | 查询所有告警规则 |
| **优先级** | P1 |
| **请求** | `GET /api/v1/alert/rule/list` |
| **预期结果** | 返回规则列表，含 name, type, config, isActive |
| **状态** | ✅ 通过 |

#### TC-S2D9-027：PUT /api/v1/alert/rule/{id}/toggle — 启用/禁用

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D9-027 |
| **测试项** | 切换规则的启用状态 |
| **优先级** | P1 |
| **请求** | `PUT /api/v1/alert/rule/{id}/toggle` |
| **预期结果** | `is_active` 取反 |
| **状态** | ✅ 通过 |

---

## 8. 测试结果汇总

| 用例 ID | 名称 | 优先级 | 状态 |
|:--------|:-----|:------:|:----:|
| TC-S2D9-001 | RED 告警判定 | P0 | ✅ |
| TC-S2D9-002 | ORANGE 告警判定 | P0 | ✅ |
| TC-S2D9-003 | YELLOW 告警判定 | P0 | ✅ |
| TC-S2D9-004 | 安全状态不触发 | P0 | ✅ |
| TC-S2D9-005 | 配置解析失败容错 | P1 | ✅ |
| TC-S2D9-006 | threshold ≤ 0 不判定 | P1 | ✅ |
| TC-S2D9-007 | 冷却时间读取 | P1 | ✅ |
| TC-S2D9-008 | 默认冷却时间 | P1 | ✅ |
| TC-S2D9-009 | RED aiAnalysis 文案 | P0 | ✅ |
| TC-S2D9-010 | ORANGE aiAnalysis 文案 | P0 | ✅ |
| TC-S2D9-011 | YELLOW aiAnalysis 文案 | P0 | ✅ |
| TC-S2D9-012 | RED suggestion 措施 | P1 | ✅ |
| TC-S2D9-013 | 三级递进关系 | P1 | ✅ |
| TC-S2D9-014 | LLM 研判正常返回 | P1 | ✅ |
| TC-S2D9-015 | LLM 失败规则降级 | P1 | ✅ |
| TC-S2D9-016 | GET 缓存读取不调 LLM | P1 | ✅ |
| TC-S2D9-017 | POST 显式重新研判 | P1 | ✅ |
| TC-S2D9-018 | WebSocket 连接建立 | P0 | ✅ |
| TC-S2D9-019 | /topic/load 负荷推送 | P0 | ✅ |
| TC-S2D9-020 | /topic/alerts 告警推送 | P0 | ✅ |
| TC-S2D9-021 | /topic/predictions 预测推送 | P1 | ✅ |
| TC-S2D9-022 | 告警分页查询 | P0 | ✅ |
| TC-S2D9-023 | 告警级别过滤 | P1 | ✅ |
| TC-S2D9-024 | 标记已读 | P1 | ✅ |
| TC-S2D9-025 | 红色告警建单 | P0 | ✅ |
| TC-S2D9-026 | 规则列表查询 | P1 | ✅ |
| TC-S2D9-027 | 规则启用/禁用切换 | P1 | ✅ |

> **Day 9 用例总计**：**27 条**（NFZ-3 告警引擎 13 + LLM 研判 4 + WebSocket 4 + 告警接口 6）

---

> 📎 **下一步**：Day 10 测试 → [Sprint2-Day10-测试报告.md](./Sprint2-Day10-测试报告.md)
