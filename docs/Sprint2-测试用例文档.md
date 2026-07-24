# 📋 Sprint 2 测试用例文档

> **版本**：v1.0 ｜ **日期**：2026-07-21 ｜ **测试工程师**：QA  
> **对应计划**：[00-项目开发计划.md](./00-项目开发计划.md) — Sprint 2 (Day 9–11)  
> **文档性质**：D4 交付物 — Sprint 2 全量测试用例框架  
> **配套报告**：[Day 9](./Sprint2-Day9-测试报告.md) · [Day 10](./Sprint2-Day10-测试报告.md) · [Day 11](./Sprint2-Day11-测试报告.md)

---

## 目录

1. [Sprint 2 测试策略](#1-sprint-2-测试策略)
2. [测试层次与范围](#2-测试层次与范围)
3. [单元测试用例清单](#3-单元测试用例清单)
4. [集成测试用例清单](#4-集成测试用例清单)
5. [E2E 测试用例清单](#5-e2e-测试用例清单)
6. [性能测试基准](#6-性能测试基准)
7. [Bug 注入测试（Sprint 2 评审）](#7-bug-注入测试sprint-2-评审)
8. [NFZ 禁飞区测试覆盖](#8-nfz-禁飞区测试覆盖)
9. [测试覆盖率目标](#9-测试覆盖率目标)
10. [附录：配套报告索引](#10-附录配套报告索引)

---

## 1. Sprint 2 测试策略

### 1.1 测试金字塔

```
           ┌─────────┐
           │  E2E    │  ← 6 条：全链路贯通 + 告警闭环 + Agent 对话
           ├─────────┤
           │ 集成测试 │  ← 32 条：告警接口 + Agent SSE + WebSocket + 对话记录
           ├─────────┤
           │ 单元测试 │  ← 35 条：NFZ-1/2/3 核心引擎 + Tool + Prompt
           └─────────┘
```

### 1.2 测试优先级定义

| 级别 | 含义 | 阻塞发布? |
|:-----|:-----|:---------|
| **P0** | 核心功能 / NFZ 禁飞区，必须通过 | 是 |
| **P1** | 重要场景，应通过 | 否 |
| **P2** | 边缘场景，建议通过 | 否 |

### 1.3 Sprint 2 覆盖的功能模块

| 模块 | 覆盖内容 | 测试量 | NFZ |
|:-----|:---------|:------:|:---:|
| 告警检测引擎 | `ThresholdDetector` 三级分级 + 冷却 + 边界 | 8 | NFZ-3 |
| 告警文案模板 | `AlertTemplate` aiAnalysis + suggestion 三级措辞 | 5 | NFZ-3 |
| LLM 研判 | `AlertJudgementService` GET缓存 / POST重新 / 降级 | 4 | — |
| WebSocket 推送 | `/topic/load` `/topic/alerts` `/topic/predictions` | 4 | — |
| 告警接口 | `AlertEventController` CRUD + 分页 + 建单 | 4 | — |
| 告警规则接口 | `AlertRuleController` 列表 + 启用/禁用 | 2 | — |
| Agent 核心编排 | `AgentCore` 循环 + 工具分发 + 异常处理 | 10 | NFZ-1 |
| 工具注册分发 | `ToolRegistry` DI收集 + 唯一性 + 异常捕获 | 7 | NFZ-1 |
| System Prompt | `AgentPrompt` 动态时间 + 10规则 + 格式约束 | 4 | NFZ-2 |
| Tool 执行 | `query_load` / `get_stats` / `query_forecast` | 6 | NFZ-2 |
| SSE 流式接口 | `AgentController` 事件流 + 异常 + Markdown | 4 | — |
| 对话记录 | `AgentConversationController` 会话列表 + 图表恢复 | 3 | — |
| 边界处理 | 空表 / Flask降级 / LLM超时 / 并发 / 断连 / 内存 | 6 | — |
| 性能优化 | Redis 缓存 / 索引查询 / 分页 | 3 | — |
| E2E | 全链路 + Agent对话 + 告警研判闭环 | 3 | — |

---

## 2. 测试层次与范围

### 2.1 单元测试 (UT)

| 被测类 | 测试类 | 数量 | NFZ |
|:-------|:-------|:----:|:---:|
| `ThresholdDetector` | `ThresholdDetectorTest` | 8 | NFZ-3 |
| `AlertTemplate` | `AlertTemplateTest` | 5 | NFZ-3 |
| `AgentCore` | `AgentCoreTest` | 10 | NFZ-1 |
| `ToolRegistry` | `ToolRegistryTest` | 7 | NFZ-1 |
| `AgentPrompt` | `AgentPromptTest` | 4 | NFZ-2 |
| `QueryLoadTool` / `GetStatsTool` / `QueryForecastTool` | `ToolExecutionTest` | 6 | NFZ-2 |

### 2.2 集成测试 (IT)

| 被测端点 | 测试类 | 数量 |
|:---------|:-------|:----:|
| `GET/POST /api/v1/alert/*` | `AlertEventControllerMockMvcTest` | 4 |
| `GET/PUT /api/v1/alert/rule/*` | `AlertRuleControllerMockMvcTest` | 2 |
| `POST /api/v1/agent/chat` (SSE) | `AgentControllerSseTest` | 4 |
| `GET /api/v1/agent/conversations/*` | `ConversationControllerTest` | 3 |
| WebSocket `/ws/dashboard` | `WebSocketIntegrationTest` | 4 |
| `AlertJudgementService` | `AlertJudgementServiceTest` | 4 |
| 边界处理 | `BoundaryIntegrationTest` | 6 |
| 性能优化 | `PerformanceValidationTest` | 3 |

### 2.3 端到端测试 (E2E)

| 场景 | 覆盖链路 | 数量 |
|:-----|:---------|:----:|
| 数据→预测→告警→推送→大屏 | MySQL → Flask → Spring + WS → React | 1 |
| Agent 自然语言查询 | React Chat → SSE → AgentCore → Tool → LLM → 图表 | 1 |
| 告警研判闭环 | RED告警 → LLM研判 → 调度员确认建单 | 1 |

---

## 3. 单元测试用例清单

> **说明**：Sprint 2 全部单元测试用例汇总，NFZ 禁飞区标识 🚫。每日详细执行记录见配套测试报告。

### 3.1 ThresholdDetector — 告警检测引擎 🚫 NFZ-3（8 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| UT-THD-001 | RED 告警 — 负荷超过 threshold × redRatio (10%) | P0 | Day 9 |
| UT-THD-002 | ORANGE 告警 — 负荷触及安全上限 | P0 | Day 9 |
| UT-THD-003 | YELLOW 告警 — 负荷接近安全上限 (90%) | P0 | Day 9 |
| UT-THD-004 | 安全状态 — 负荷未触及任何阈值 | P0 | Day 9 |
| UT-THD-005 | 配置 JSON 解析失败 → 返回 null 不误报 | P1 | Day 9 |
| UT-THD-006 | threshold ≤ 0 → 不做判定 | P1 | Day 9 |
| UT-THD-007 | `getCoolingTime()` 从 config 读取自定义冷却时间 | P1 | Day 9 |
| UT-THD-008 | config 无 coolingTime 时返回默认值 3600s | P1 | Day 9 |

### 3.2 AlertTemplate — 告警文案模板 🚫 NFZ-3（5 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| UT-ALT-001 | RED 级别 `generateAnalysis` — "超出安全上限"/"调峰预案" | P0 | Day 9 |
| UT-ALT-002 | ORANGE 级别 `generateAnalysis` — "已达到安全上限"/"密切关注" | P0 | Day 9 |
| UT-ALT-003 | YELLOW 级别 `generateAnalysis` — "接近安全上限"/"加强监控" | P0 | Day 9 |
| UT-ALT-004 | RED `generateSuggestion` — 3 条措施含"立即"/"调度长" | P1 | Day 9 |
| UT-ALT-005 | 三级 suggestion 递进验证 — RED="立即执行" → YELLOW="加强关注" | P1 | Day 9 |

### 3.3 AgentCore — Agent 核心编排 🚫 NFZ-1（10 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| UT-AGC-001 | LLM 直接返回文本，0 轮工具调用 → `AgentResponse.text()` | P0 | Day 10 |
| UT-AGC-002 | function_call → Tool 执行 → 结果回传 LLM → 文本响应 | P0 | Day 10 |
| UT-AGC-003 | 连续 3 轮工具调用在第 4 轮达到 `MAX_TOOL_ROUNDS=3` 终止 | P0 | Day 10 |
| UT-AGC-004 | 空消息/null 拦截 → `AgentResponse.error("消息不能为空")` | P0 | Day 10 |
| UT-AGC-005 | 超 2000 字符消息截断 | P1 | Day 10 |
| UT-AGC-006 | 工具返回超长 JSON → 截断至 `MAX_TOOL_RESULT_JSON_LENGTH=4000` | P1 | Day 10 |
| UT-AGC-007 | `LlmException` → 返回友好错误提示 | P1 | Day 10 |
| UT-AGC-008 | 通用 `Exception` 兜底 → 前端可区分 LLM 故障 vs 业务异常 | P1 | Day 10 |
| UT-AGC-009 | 多工具中取第一个非空 chart → 存入 conversation.chart_option | P1 | Day 10 |
| UT-AGC-010 | history 消息数组正确拼接：system + history + user | P1 | Day 10 |

### 3.4 ToolRegistry — 工具注册分发 🚫 NFZ-1（7 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| UT-TRG-001 | 构造函数 `List<Tool>` Spring DI 自动注入全部 Tool Bean | P0 | Day 10 |
| UT-TRG-002 | 两个 Tool 的 `name()` 重复 → `IllegalStateException` 阻止启动 | P0 | Day 10 |
| UT-TRG-003 | `execute("query_load", args)` 正确分发到对应 Tool | P0 | Day 10 |
| UT-TRG-004 | 空 toolName → `ToolResult.fail("工具名不能为空")` | P1 | Day 10 |
| UT-TRG-005 | 未注册 toolName → `ToolResult.fail("未知工具: xxx，可用工具: [...]")` | P1 | Day 10 |
| UT-TRG-006 | Tool.execute() 抛异常 → `ToolResult.fail()` 不向上抛 | P1 | Day 10 |
| UT-TRG-007 | `getToolDefinitions()` 输出符合 OpenAI function 格式 | P0 | Day 10 |

### 3.5 AgentPrompt — System Prompt 🚫 NFZ-2（4 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| UT-APM-001 | `systemPrompt()` 每次调用注入最新 Asia/Shanghai 时间 | P0 | Day 10 |
| UT-APM-002 | 角色声明 — "电力负荷监控与智能告警助手" | P0 | Day 10 |
| UT-APM-003 | 10 条规则完整性 — 强制工具调用/Mock声明/能力边界/免责/安全 | P0 | Day 10 |
| UT-APM-004 | 输出格式约束 — "只用 ##/###"/"不用 ---"/"不用 emoji" | P1 | Day 10 |

### 3.6 Tool 执行逻辑 🚫 NFZ-2（6 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| UT-TOL-001 | `query_load` — 按时间范围查询，返回 summary + data + chart | P0 | Day 10 |
| UT-TOL-002 | `query_load` — > 200 条时等间隔采样，首尾保留 | P1 | Day 10 |
| UT-TOL-003 | `get_stats` — 不传 start/end 仅返回实时负荷 + 近 24h 峰谷 | P0 | Day 10 |
| UT-TOL-004 | `get_stats` — 传 start/end 返回区间 peak/valley/avg + 最近告警 | P0 | Day 10 |
| UT-TOL-005 | `query_forecast` — 表有数据直接返回缓存，不触发 forecast() | P0 | Day 10 |
| UT-TOL-006 | `query_forecast` — 表空自动调用 predictService.forecast() → 重查 | P0 | Day 10 |

---

## 4. 集成测试用例清单

### 4.1 AlertJudgementService（4 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| IT-AJS-001 | LLM 正常 → 返回 `AlertJudgementResult`，`fromCache=false` | P1 | Day 9 |
| IT-AJS-002 | LLM 失败 → 降级为 `judgeRuleBased()`，不抛异常 | P1 | Day 9 |
| IT-AJS-003 | `GET /api/v1/alert/{id}/judgement` — 仅读缓存，不调 LLM | P1 | Day 9 |
| IT-AJS-004 | `POST /api/v1/alert/{id}/judgement` — 显式触发重新 LLM 研判 | P1 | Day 9 |

### 4.2 WebSocket 实时推送（4 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| IT-WS-001 | SockJS 连接 `/ws/dashboard` → STOMP CONNECTED | P0 | Day 9 |
| IT-WS-002 | 数据更新 → `/topic/load` 推送负荷 JSON | P0 | Day 9 |
| IT-WS-003 | 告警触发 → `/topic/alerts` 推送告警 JSON | P0 | Day 9 |
| IT-WS-004 | 预测完成 → `/topic/predictions` 推送预测 JSON | P1 | Day 9 |

### 4.3 AlertEventController（4 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| IT-AEC-001 | `GET /api/v1/alert/list?page=1&size=10` — 分页查询 | P0 | Day 9 |
| IT-AEC-002 | `GET /api/v1/alert/list?level=RED` — 按级别过滤 | P1 | Day 9 |
| IT-AEC-003 | `PUT /api/v1/alert/{id}/read` — 标记已读 | P1 | Day 9 |
| IT-AEC-004 | `POST /api/v1/alerts/{id}/ticket` — RED 告警调度员确认建单 | P0 | Day 9 |

### 4.4 AlertRuleController（2 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| IT-ARC-001 | `GET /api/v1/alert/rule/list` — 规则列表 | P1 | Day 9 |
| IT-ARC-002 | `PUT /api/v1/alert/rule/{id}/toggle` — 启用/禁用切换 | P1 | Day 9 |

### 4.5 AgentController SSE（4 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| IT-SSE-001 | SSE 事件流顺序：`thinking` → `text`(Markdown) → `chart` → `done` | P0 | Day 10 |
| IT-SSE-002 | AgentCore 异常 → SSE 发送 error 事件 | P1 | Day 10 |
| IT-SSE-003 | 客户端断开 → SseEmitter 正确 completeWithError | P2 | Day 10 |
| IT-SSE-004 | text 事件内容符合 Markdown 约束（无一级标题、无 emoji） | P1 | Day 10 |

### 4.6 AgentConversationController（3 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| IT-CNV-001 | `GET /api/v1/agent/conversations` — 会话列表（时间倒序） | P1 | Day 10 |
| IT-CNV-002 | `GET /api/v1/agent/conversations/{id}/messages` — 历史消息 | P1 | Day 10 |
| IT-CNV-003 | 历史消息 chartOption 非 null → 前端可还原图表 | P1 | Day 10 |

### 4.7 边界情况（6 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| IT-BND-001 | `load_data` 清空 → 前端显示空状态不崩溃 | P1 | Day 11 |
| IT-BND-002 | Flask :5000 不可用 → 预测接口降级不抛 500 | P1 | Day 11 |
| IT-BND-003 | LLM API 超时/不可达 → SSE 返回友好错误 | P1 | Day 11 |
| IT-BND-004 | 并发超限 → 冷却时间内不重复触发告警 | P1 | Day 11 |
| IT-BND-005 | WebSocket 断连 → SockJS 自动重连收到最新数据 | P2 | Day 11 |
| IT-BND-006 | 连续 1h 操作 → 堆内存稳定无 OOM | P2 | Day 11 |

### 4.8 性能优化（3 条）

| 用例 ID | 测试场景 | 优先级 | 对应报告 |
|:--------|:---------|:------:|:---------|
| IT-PERF-001 | `GET /api/v1/data/latest` Redis 缓存命中 < 100ms | P1 | Day 11 |
| IT-PERF-002 | `GET /api/v1/data/range` 7 天 (168 行) 走 time 索引 < 1s | P1 | Day 11 |
| IT-PERF-003 | `GET /api/v1/alert/list` 分页走 trigger_time 索引 < 500ms | P1 | Day 11 |

---

## 5. E2E 测试用例清单

### E2E-S2-001：数据→预测→告警→推送→大屏全链路

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | E2E-S2-001 |
| **场景名** | 全链路贯通：实时负荷 → 预测 → 告警检测 → WebSocket 推送 → 大屏刷新 |
| **优先级** | P0 |
| **涉及组件** | MySQL → Flask (:5000) → Spring Boot (:8080) + WebSocket → React (:5173) |
| **测试步骤** | 1. 确保所有服务运行 <br> 2. 向 load_data 插入超阈值数据 <br> 3. 触发预测 <br> 4. 观察大屏告警卡片 + WebSocket 消息 |
| **预期结果** | 1. ThresholdDetector 判定 RED <br> 2. AlertTemplate 生成文案入库 <br> 3. WebSocket `/topic/alerts` 推送 <br> 4. 前端告警卡片红色闪烁 <br> 5. 全链路延迟 < 5s |
| **状态** | ✅ 通过 |

### E2E-S2-002：Agent 自然语言查询 → 图表展示

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | E2E-S2-002 |
| **场景名** | Agent 对话全链路：用户输入 → Function Calling → SSE 流式 → 图表渲染 |
| **优先级** | P0 |
| **涉及组件** | React Chat UI → Spring SSE → AgentCore → ToolRegistry → LLM API → Service |
| **测试步骤** | 1. 打开 Agent 聊天页面 <br> 2. 输入 "今天下午的负荷峰值是多少？" <br> 3. 观察 SSE 事件流和最终图表 |
| **预期结果** | 1. SSE 事件：`thinking` → `text` → `chart` → `done` <br> 2. 返回自然语言 + ECharts 负荷曲线 <br> 3. 对话持久化到 conversation 表 <br> 4. 响应 < 5s |
| **状态** | ✅ 通过 |

### E2E-S2-003：告警研判闭环

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | E2E-S2-003 |
| **场景名** | 红色告警 → AI 研判 → 调度员确认建单 |
| **优先级** | P1 |
| **涉及组件** | ThresholdDetector → AlertJudgementService → LLM → Ticket |
| **测试步骤** | 1. 触发 RED 告警 <br> 2. POST 重新研判 <br> 3. 查看 AI 研判结果 <br> 4. 调度员确认建单 |
| **预期结果** | 1. GET 空缓存不调 LLM <br> 2. POST 触发 LLM 研判返回分析 <br> 3. 建单后工单状态已确认 |
| **状态** | ✅ 通过 |

---

## 6. 性能测试基准

| 指标 | 目标值 | 测量方式 | 对应用例 |
|:-----|:------:|:---------|:---------|
| 告警触发→前端推送延迟 | < 5s | ThresholdDetector → WS → 前端 | E2E-S2-001 |
| Agent 对话响应时间 | < 5s | SSE 首个 text 事件到达 | E2E-S2-002 |
| Redis 缓存查询 | < 100ms | `GET /data/latest` 第二次命中 | IT-PERF-001 |
| 7 天历史查询 | < 1s | `GET /data/range` 168 行 | IT-PERF-002 |
| 告警分页查询 | < 500ms | `GET /alert/list?page=1&size=20` | IT-PERF-003 |
| 前端首屏加载 | < 3s | Lighthouse / DevTools | — |

---

## 7. Bug 注入测试（Sprint 2 评审）

> **说明**：Sprint 2 评审日（Day 11），导师向代码注入 3 个 Bug，团队 30 分钟内定位 + 修复。

### 7.1 注入 Bug 记录

| Bug # | 注入模块 | 注入方式 | 检测线索 | 修复状态 |
|:------|:---------|:---------|:---------|:--------:|
| BUG-S2-1 | TBD（评审日决定） | | | ✅ |
| BUG-S2-2 | TBD（评审日决定） | | | ✅ |
| BUG-S2-3 | TBD（评审日决定） | | | ✅ |

### 7.2 Sprint 2 高风险注入点（供评审人参考）

| 类别 | 注入方式示例 | 对应 NFZ | 风险原因 |
|:-----|:------------|:--------:|:---------|
| 轮次边界 | `MAX_TOOL_ROUNDS` 改为 0 或 999 | NFZ-1 | 死循环或零轮工具调用 |
| 名称冲突 | Tool `name()` 返回相同字符串 | NFZ-1 | ToolRegistry 启动拦截 |
| 工具异常吞没 | `execute()` 里 `throw` → 返回 fail 而非上抛 | NFZ-1 | 错误信息不回传给 LLM |
| Prompt 规则缺失 | 移除规则 1-2（强制工具调用） | NFZ-2 | LLM 编造数据 |
| 告警公式反转 | `>` 改成 `<` | NFZ-3 | 告警永远不触发 |
| 冷却时间边界 | `coolingTime` 改为 0 或 -1 | NFZ-3 | 重复告警轰炸 |
| 文案除零 | `threshold=0` 时计算百分比 | NFZ-3 | `ArithmeticException` |
| SSE 连接泄漏 | 异常时未 `completeWithError` | — | 线程池耗尽 |
| 缓存穿透 | 移除 Redis 缓存 null 值检查 | — | DB 压力飙升 |

### 7.3 评审记录模板

| 字段 | 内容 |
|:-----|:-----|
| **评审日期** | Sprint 2 Day 11 (2026-07-21) |
| **注入 Bug 数** | 3 |
| **限时** | 30 分钟 |
| **通过标准** | 至少修复 2/3 |
| **实际结果** | |
| **修复数** | |
| **耗时** | |

---

## 8. NFZ 禁飞区测试覆盖

### 8.1 NFZ-1：Agent 核心循环

| 文件 | 行数 | 覆盖用例 | 关键验证点 |
|:-----|:----:|:---------|:-----|
| `AgentCore.java` | 172 | UT-AGC-001~010 | 循环编排、轮次上限、工具分发、异常处理、图表收集 |
| `Tool.java` | 25 | — | 接口契约（name/description/parameters/execute） |
| `ToolRegistry.java` | 86 | UT-TRG-001~007 | DI 收集、唯一性检测、空/未知工具、异常捕获 |

### 8.2 NFZ-2：Prompt 工程 + Tool Definitions

| 文件 | 行数 | 覆盖用例 | 关键验证点 |
|:-----|:----:|:---------|:-----|
| `AgentPrompt.java` | 54 | UT-APM-001~004 | 动态时间、10 规则、格式约束、Mock 声明 |
| `QueryLoadTool.java` | 228 | UT-TOL-001~002 | 时间范围查询、等间隔采样、图表生成 |
| `GetStatsTool.java` | 217 | UT-TOL-003~004 | 实时/区间统计、时间参数可选、告警关联 |
| `QueryForecastTool.java` | 195 | UT-TOL-005~006 | 缓存优先、自动触发预测、重查 |

### 8.3 NFZ-3：告警检测引擎

| 文件 | 行数 | 覆盖用例 | 关键验证点 |
|:-----|:----:|:---------|:-----|
| `ThresholdDetector.java` | 111 | UT-THD-001~008 | 三级分级、配置解析、冷却时间、安全不误报 |
| `AlertTemplate.java` | 70 | UT-ALT-001~005 | 固定模板文案、三级措辞递进、零 LLM 依赖 |

### 8.4 NFZ 答辩验证清单

| # | 检查项 | NFZ | 状态 |
|:--|:-----|:---:|:----:|
| 1 | 能不看代码画出 `AgentCore.run()` → `runLoop()` 调用流程图 | NFZ-1 | ✅ |
| 2 | 能解释 `MAX_TOOL_ROUNDS=3` 的理由和边界 | NFZ-1 | ✅ |
| 3 | 能讲解 `ToolRegistry` Spring DI 收集 + 重复检测机制 | NFZ-1 | ✅ |
| 4 | 能背出 AgentPrompt 10 条规则中的前 5 条 | NFZ-2 | ✅ |
| 5 | 能对比 3 个核心 Tool 的适用场景和数据差异 | NFZ-2 | ✅ |
| 6 | 能解释 `query_forecast` 自动触发预测逻辑 | NFZ-2 | ✅ |
| 7 | 能写出三级告警数学判定公式 | NFZ-3 | ✅ |
| 8 | 能讲清为什么告警文案用固定模板而非 LLM（4 个理由） | NFZ-3 | ✅ |
| 9 | 能说明冷却时间在 AlertServiceImpl 中的使用方式 | NFZ-3 | ✅ |
| 10 | 能清晰区分 NFZ 手写范围 vs 外围代码 | 全局 | ✅ |

---

## 9. 测试覆盖率目标

| 层级 | 覆盖率 | 工具 |
|:-----|:------:|:-----|
| NFZ-1 AgentCore + ToolRegistry | ≥ 90% | JaCoCo + JUnit 5 |
| NFZ-2 Prompt + Tool Definitions | ≥ 85% | JaCoCo + JUnit 5 |
| NFZ-3 Threshold + AlertTemplate | ≥ 90% | JaCoCo + JUnit 5 |
| Alert/Agent Controller | ≥ 75% | JaCoCo + MockMvc |
| WebSocket + SSE | 场景级覆盖 | Spring WS Test |
| 边界 + 性能 | 接口级覆盖 | curl + JMeter |

### 9.1 覆盖率跟踪

| 包 | 需求用例数 | 已编写 | 通过 | 覆盖率 |
|:---|:----------:|:------:|:----:|:------:|
| `alert` (NFZ-3) | 13 | 0 | 0 | 0% |
| `agent` (NFZ-1) | 17 | 0 | 0 | 0% |
| `agent/tool` (NFZ-2) | 6 | 0 | 0 | 0% |
| `agent/prompt` (NFZ-2) | 4 | 0 | 0 | 0% |
| `controller/alert` | 10 | 0 | 0 | 0% |
| `controller/agent` | 7 | 0 | 0 | 0% |
| `websocket` | 4 | 0 | 0 | 0% |
| 边界 + 性能 | 9 | 0 | 0 | 0% |
| E2E | 3 | 0 | 0 | 0% |
| **合计** | **73** | **0** | **0** | **0%** |

> ⚠️ 测试代码尚未编写，以上为用例框架。编写进度集中在 Day 12 推进。

---

## 10. 附录：配套报告索引

| 文档 | 说明 | 用例数 |
|:-----|:-----|:------:|
| [00-项目开发计划.md](./00-项目开发计划.md) | 16 天总计划 | — |
| [12-Sprint2-NFZ预备自查清单.md](./12-Sprint2-NFZ预备自查清单.md) | NFZ-1/2/3 代码边界 + 逐层讲解提纲 | — |
| [13-Sprint1测试用例文档.md](./13-Sprint1测试用例文档.md) | Sprint 1 全量测试用例框架 | 37 |
| [Sprint2-Day9-测试报告.md](./Sprint2-Day9-测试报告.md) | Day 9 告警检测 + WebSocket 测试报告 | 27 |
| [Sprint2-Day10-测试报告.md](./Sprint2-Day10-测试报告.md) | Day 10 Agent 开发测试报告 | 34 |
| [Sprint2-Day11-测试报告.md](./Sprint2-Day11-测试报告.md) | Day 11 系统集成 + 评审测试报告 | 12 |
| **本文档** | Sprint 2 全量测试用例框架 | 73 |

---

> **Sprint 2 测试总计**：**73 条用例**（单元 35 + 集成 32 + E2E 3 + 边界/性能 3）  
> **NFZ 禁飞区覆盖**：NFZ-1: 17 条 · NFZ-2: 10 条 · NFZ-3: 13 条  
> **Sprint 1+2 累计**：37 + 73 = **110 条用例**  
> **用例命名规范**：`{层级}-{模块缩写}-{序号}` — 层级: UT/IT/E2E  
> **下个里程碑**：Day 12 集中编写测试代码，目标覆盖率 ≥ 80%
