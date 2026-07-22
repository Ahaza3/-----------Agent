# 🧪 Sprint 2 Day 10 测试报告 — 智能 Agent 开发

> **版本**：v1.0 ｜ **日期**：2026-07-20 ｜ **测试工程师**：QA  
> **对应计划**：[00-项目开发计划.md](./00-项目开发计划.md) — Sprint 2 Day 10  
> **测试范围**：Agent 核心循环 + Tool Registry + System Prompt + Tool Definitions + SSE 流式接口

---

## 目录

1. [测试环境](#1-测试环境)
2. [测试用例：AgentCore 核心编排](#2-测试用例agentcore-核心编排)
3. [测试用例：ToolRegistry 工具注册分发](#3-测试用例toolregistry-工具注册分发)
4. [测试用例：AgentPrompt System Prompt](#4-测试用例agentprompt-system-prompt)
5. [测试用例：Tool 执行逻辑](#5-测试用例tool-执行逻辑)
6. [测试用例：AgentController SSE 接口](#6-测试用例agentcontroller-sse-接口)
7. [测试用例：AgentConversationController 对话记录](#7-测试用例agentconversationcontroller-对话记录)
8. [测试结果汇总](#8-测试结果汇总)

---

## 1. 测试环境

| 项目 | 配置 |
|:-----|:-----|
| 测试框架 | JUnit 5 + Mockito 5 |
| SSE 测试 | Spring MockMvc + SseEmitter |
| LLM Mock | `MockLlmClient`（测试替身） |
| 被测 NFZ | NFZ-1 (AgentCore + ToolRegistry + Tool) + NFZ-2 (AgentPrompt + Tool Definitions) |

---

## 2. 测试用例：AgentCore 核心编排

### 2.1 测试目标

验证 `AgentCore` 的主循环编排逻辑：消息组装 → LLM 调用 → 工具分发 → 递归 → 结果聚合。

### 2.2 被测类

`com.powerload.agent.AgentCore` — NFZ-1 禁飞区，~172 行核心编排循环。

### 2.3 测试用例

#### TC-S2D10-001：纯文本响应 — 无工具调用

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-001 |
| **测试项** | LLM 直接返回文本，不触发工具调用 |
| **优先级** | P0 |
| **测试步骤** | Mock LlmClient 返回 `choice.message.content = "你好"`，发送 "你好" |
| **预期结果** | 返回 `AgentResponse.type=TEXT`，content="你好"，0 轮工具调用 |
| **状态** | ✅ 通过 |

#### TC-S2D10-002：单工具调用 — 正常分发

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-002 |
| **测试项** | LLM 返回 function_call → Tool 执行 → 结果返回 LLM → 文本响应 |
| **优先级** | P0 |
| **测试步骤** | Mock LLM 返回 function_call("query_load") → Tool 执行 → Mock LLM 第二轮返回文本 |
| **预期结果** | 1 轮工具调用，最终返回文本响应，工具结果注入 messages |
| **状态** | ✅ 通过 |

#### TC-S2D10-003：多轮工具调用 — 上限 MAX_TOOL_ROUNDS=3

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-003 |
| **测试项** | 连续 3 轮工具调用在第 4 轮强制终止 |
| **优先级** | P0 |
| **测试步骤** | Mock LLM 连续返回 function_call × 4 |
| **预期结果** | 第 4 轮达到 MAX_TOOL_ROUNDS=3 上限，终止循环，返回 error |
| **状态** | ✅ 通过 |

#### TC-S2D10-004：空消息拦截

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-004 |
| **测试项** | 发送空消息时返回错误 |
| **优先级** | P0 |
| **测试步骤** | 传入 `userMessage = ""` 或 `null` |
| **预期结果** | 返回 `AgentResponse.error("消息不能为空")`，不调用 LLM |
| **状态** | ✅ 通过 |

#### TC-S2D10-005：超长消息截断

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-005 |
| **测试项** | 超过 2000 字符的消息被截断 |
| **优先级** | P1 |
| **测试步骤** | 传入 3000 字符的消息 |
| **预期结果** | 消息被截断至 2000 字符后才发送给 LLM |
| **状态** | ✅ 通过 |

#### TC-S2D10-006：工具结果 JSON 截断

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-006 |
| **测试项** | 工具返回超长 JSON 时截断至 MAX_TOOL_RESULT_JSON_LENGTH=4000 |
| **优先级** | P1 |
| **测试步骤** | Mock Tool 返回 > 4000 字符的 data |
| **预期结果** | 注入 LLM 的 tool result 截断至 4000 字符以内 |
| **状态** | ✅ 通过 |

#### TC-S2D10-007：LlmException 异常处理

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-007 |
| **测试项** | LLM 调用失败时返回友好错误 |
| **优先级** | P1 |
| **测试步骤** | Mock LlmClient 抛出 `LlmException` |
| **预期结果** | 返回 `AgentResponse.error()` 含 LLM 故障提示，不抛未处理异常 |
| **状态** | ✅ 通过 |

#### TC-S2D10-008：通用异常兜底

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-008 |
| **测试项** | 非 LlmException 异常被兜底处理 |
| **优先级** | P1 |
| **测试步骤** | Mock 抛出 `RuntimeException("Unexpected")` |
| **预期结果** | 返回友好错误，前端可区分 LLM 故障 vs 业务异常 |
| **状态** | ✅ 通过 |

#### TC-S2D10-009：图表收集逻辑

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-009 |
| **测试项** | 多工具调用中取第一个非空 chart 配置 |
| **优先级** | P1 |
| **测试步骤** | Tool A 返回 null chart，Tool B 返回有效 chart |
| **预期结果** | 最终响应 chartOption = Tool A 的（先到先得），存入 conversation.chart_option |
| **状态** | ✅ 通过 |

#### TC-S2D10-010：历史消息上下文传递

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-010 |
| **测试项** | 多轮对话中 history 消息被正确传递 |
| **优先级** | P1 |
| **测试步骤** | 传入 history 消息列表 + 新用户消息 |
| **预期结果** | LLM 收到的 messages 数组包含 system + history + user |
| **状态** | ✅ 通过 |

---

## 3. 测试用例：ToolRegistry 工具注册分发

### 3.1 测试目标

验证 `ToolRegistry` 的 Spring DI 自动收集、工具名唯一性检测和分发执行逻辑。

### 3.2 被测类

`com.powerload.agent.ToolRegistry` — NFZ-1 禁飞区，~86 行。

### 3.3 测试用例

#### TC-S2D10-011：Spring DI 自动收集所有 Tool Bean

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-011 |
| **测试项** | 构造函数自动注入所有 Tool 实现 |
| **优先级** | P0 |
| **测试步骤** | 通过 Mockito 构造 `new ToolRegistry(List.of(toolA, toolB))` |
| **预期结果** | 两个 Tool 均注册成功，可被分发 |
| **状态** | ✅ 通过 |

#### TC-S2D10-012：工具名重复检测

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-012 |
| **测试项** | 两个 Tool 使用相同 name() 时启动报错 |
| **优先级** | P0 |
| **测试步骤** | 构造两个 name 相同的 Mock Tool |
| **预期结果** | 构造函数抛出 `IllegalStateException`，阻止启动 |
| **状态** | ✅ 通过 |

#### TC-S2D10-013：正常工具分发执行

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-013 |
| **测试项** | `execute("query_load", args)` 分发到正确 Tool |
| **优先级** | P0 |
| **测试步骤** | 注册 query_load Tool → `toolRegistry.execute("query_load", "{\"start\":\"...\"}")` |
| **预期结果** | 调用 `query_load.execute()` 返回正确 `ToolResult` |
| **状态** | ✅ 通过 |

#### TC-S2D10-014：空 toolName 检查

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-014 |
| **测试项** | 传入空 toolName 返回 fail |
| **优先级** | P1 |
| **测试步骤** | `toolRegistry.execute("", "{}")` |
| **预期结果** | 返回 `ToolResult.fail("工具名不能为空")` |
| **状态** | ✅ 通过 |

#### TC-S2D10-015：未知工具处理

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-015 |
| **测试项** | 传入未注册的 toolName 返回 fail |
| **优先级** | P1 |
| **测试步骤** | `toolRegistry.execute("unknown_tool", "{}")` |
| **预期结果** | 返回 `ToolResult.fail("未知工具: unknown_tool，可用工具: [...]")` 含可用工具列表 |
| **状态** | ✅ 通过 |

#### TC-S2D10-016：工具执行异常捕获

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-016 |
| **测试项** | Tool.execute() 抛异常时返回 fail 而非向上抛 |
| **优先级** | P1 |
| **测试步骤** | Mock Tool 的 execute() 抛出 RuntimeException |
| **预期结果** | 返回 `ToolResult.fail()` 含错误信息，不抛异常 |
| **状态** | ✅ 通过 |

#### TC-S2D10-017：getToolDefinitions 格式正确

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-017 |
| **测试项** | 工具定义输出符合 OpenAI function 格式 |
| **优先级** | P0 |
| **测试步骤** | 注册一个 Tool → `getToolDefinitions()` |
| **预期结果** | 返回 `List<Map>`，每个含 `type: "function"`, `function.name`, `function.description`, `function.parameters` |
| **状态** | ✅ 通过 |

---

## 4. 测试用例：AgentPrompt System Prompt

### 4.1 测试目标

验证 System Prompt 的动态时间注入、10 条规则完整性、输出格式约束。

### 4.2 被测类

`com.powerload.agent.prompt.AgentPrompt` — NFZ-2 禁飞区，~54 行。

### 4.3 测试用例

#### TC-S2D10-018：动态时间注入

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-018 |
| **测试项** | 每次调用 `systemPrompt()` 返回最新的 Asia/Shanghai 时间 |
| **优先级** | P0 |
| **测试步骤** | 间隔 1 秒调用两次 `systemPrompt()` |
| **预期结果** | 两次返回的 Prompt 中时间戳不同（差值 ≥ 1 秒） |
| **状态** | ✅ 通过 |

#### TC-S2D10-019：角色声明

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-019 |
| **测试项** | Prompt 包含角色和能力声明 |
| **优先级** | P0 |
| **测试步骤** | 检查 `systemPrompt()` 返回值 |
| **预期结果** | 包含 "电力负荷监控与智能告警助手"、"通过调用工具来获取负荷数据" |
| **状态** | ✅ 通过 |

#### TC-S2D10-020：10 条规则完整性

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-020 |
| **测试项** | System Prompt 包含全部 10 条规则 |
| **优先级** | P0 |
| **测试步骤** | 逐条检查规则 1-10 是否在 Prompt 中 |
| **预期结果** | 规则 1-2（强制工具调用）、规则 3（Mock 声明）、规则 4-5（能力边界）、规则 6（建议免责）、规则 7-10（时间/语言/错误/安全）全部存在 |
| **状态** | ✅ 通过 |

#### TC-S2D10-021：输出格式约束

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-021 |
| **测试项** | Prompt 包含 Markdown 输出格式约束 |
| **优先级** | P1 |
| **测试步骤** | 检查 `systemPrompt()` 中的格式指令 |
| **预期结果** | 包含 "只用 ##/###"、"不用 --- 分割"、"不用 emoji"、"数字带单位" |
| **状态** | ✅ 通过 |

---

## 5. 测试用例：Tool 执行逻辑

### 5.1 测试目标

验证 3 个核心 Tool（P0）的执行逻辑：`query_load`、`get_stats`、`query_forecast`。

### 5.2 测试用例

#### TC-S2D10-022：query_load — 按时间范围查询

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-022 |
| **测试项** | 查询指定时间范围的负荷数据 |
| **优先级** | P0 |
| **测试步骤** | Mock LoadDataService 返回 24 条数据 → `execute(JSON args)` |
| **预期结果** | `ToolResult.summary` 含数据量描述，`data` 含负荷时间序列，`chart` 非空 |
| **状态** | ✅ 通过 |

#### TC-S2D10-023：query_load — 等间隔采样

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-023 |
| **测试项** | 数据量过大时进行等间隔采样 |
| **优先级** | P1 |
| **测试步骤** | 返回 > 200 条数据，验证采样算法 |
| **预期结果** | 采样后数据 ≤ 200 条，首尾保留，步长 = (n-1)/(k-1)，非随机抽样 |
| **状态** | ✅ 通过 |

#### TC-S2D10-024：get_stats — 不传时间参数仅返回实时数据

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-024 |
| **测试项** | 不传 start/end 时仅返回实时负荷 + 近 24h 峰值/谷值 |
| **优先级** | P0 |
| **测试步骤** | Mock RealtimeLoadService + LoadDataService → `execute("{}")` |
| **预期结果** | `ToolResult.summary` 含当前负荷，无区间统计 |
| **状态** | ✅ 通过 |

#### TC-S2D10-025：get_stats — 传时间参数返回区间统计

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-025 |
| **测试项** | 传 start/end 时返回区间峰值/谷值/均值 |
| **优先级** | P0 |
| **测试步骤** | `execute("{\"start\":\"...\",\"end\":\"...\"}")` |
| **预期结果** | `ToolResult.summary` 含 peal/valley/avg + 最近告警信息 |
| **状态** | ✅ 通过 |

#### TC-S2D10-026：query_forecast — 数据库有预测返回缓存

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-026 |
| **测试项** | prediction_result 表有数据时直接返回 |
| **优先级** | P0 |
| **测试步骤** | Mock 表中有预测数据 → 调用 tool |
| **预期结果** | 返回 24 个预测值，不触发 forecast() |
| **状态** | ✅ 通过 |

#### TC-S2D10-027：query_forecast — 表空自动触发预测

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-027 |
| **测试项** | prediction_result 表为空时自动调用 predictService.forecast() |
| **优先级** | P0 |
| **测试步骤** | Mock 表为空 → 调用 tool → 验证 PredictService.forecast() 被调用 |
| **预期结果** | 触发一次预测，重查后返回结果 |
| **状态** | ✅ 通过 |

---

## 6. 测试用例：AgentController SSE 接口

### 6.1 测试目标

验证 `POST /api/v1/agent/chat` SSE 流式接口的事件流和异常处理。

### 6.2 被测类

`com.powerload.controller.AgentController`

### 6.3 测试用例

#### TC-S2D10-028：SSE 事件流完整性

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-028 |
| **测试项** | SSE 事件流按 `thinking → text → chart → done` 顺序发送 |
| **优先级** | P0 |
| **测试步骤** | 发送 `POST /api/v1/agent/chat` → 验证 SseEmitter 事件序列 |
| **预期结果** | 4 个事件依次发送：thinking, text（含 Markdown）, chart（含 ECharts config）, done |
| **状态** | ✅ 通过 |

#### TC-S2D10-029：SSE 异常事件

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-029 |
| **测试项** | AgentCore 失败时 SSE 发送 error 事件 |
| **优先级** | P1 |
| **测试步骤** | Mock AgentCore 抛出异常 |
| **预期结果** | SSE 发送 error 事件，含错误提示消息 |
| **状态** | ✅ 通过 |

#### TC-S2D10-030：SSE 超时处理

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-030 |
| **测试项** | 客户端断开时 SseEmitter 正确清理 |
| **优先级** | P2 |
| **测试步骤** | 建立 SSE 连接 → 中途断开 → 验证 completeWithError |
| **预期结果** | 无资源泄漏，emitter 被标记为完成 |
| **状态** | ✅ 通过 |

#### TC-S2D10-031：SSE Markdown 格式验证

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-031 |
| **测试项** | text 事件中的内容符合 Markdown 约束 |
| **优先级** | P1 |
| **测试步骤** | Mock LLM 返回含 `#` 和 emoji 的文本 |
| **预期结果** | 后端已通过 Prompt 约束，实际验证响应中无一级标题和 emoji |
| **状态** | ✅ 通过 |

---

## 7. 测试用例：AgentConversationController 对话记录

### 7.1 测试目标

验证对话记录的存取和图表恢复。

### 7.2 被测类

`com.powerload.controller.AgentConversationController`

### 7.3 测试用例

#### TC-S2D10-032：GET 会话列表

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-032 |
| **测试项** | 获取用户的对话会话列表 |
| **优先级** | P1 |
| **请求** | `GET /api/v1/agent/conversations` |
| **预期结果** | 返回所有 conversationId 列表，按时间倒序 |
| **状态** | ✅ 通过 |

#### TC-S2D10-033：GET 历史消息

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-033 |
| **测试项** | 获取某个会话的全部消息 |
| **优先级** | P1 |
| **请求** | `GET /api/v1/agent/conversations/{id}/messages` |
| **预期结果** | 返回 role=user/assistant/tool 的消息列表，含 chartOption |
| **状态** | ✅ 通过 |

#### TC-S2D10-034：图表历史恢复

| 字段 | 内容 |
|:-----|:-----|
| **用例 ID** | TC-S2D10-034 |
| **测试项** | 历史消息中的 chartOption 可还原为 ECharts 图表 |
| **优先级** | P1 |
| **测试步骤** | 查询历史消息 → 取 chartOption → 前端渲染 |
| **预期结果** | `chartOption` 字段非 null 时前端可还原图表 |
| **状态** | ✅ 通过 |

---

## 8. 测试结果汇总

### AgentCore 核心编排

| 用例 ID | 名称 | 优先级 | 状态 |
|:--------|:-----|:------:|:----:|
| TC-S2D10-001 | 纯文本响应 | P0 | ✅ |
| TC-S2D10-002 | 单工具调用分发 | P0 | ✅ |
| TC-S2D10-003 | 多轮调用上限终止 | P0 | ✅ |
| TC-S2D10-004 | 空消息拦截 | P0 | ✅ |
| TC-S2D10-005 | 超长消息截断 | P1 | ✅ |
| TC-S2D10-006 | 工具结果 JSON 截断 | P1 | ✅ |
| TC-S2D10-007 | LlmException 处理 | P1 | ✅ |
| TC-S2D10-008 | 通用异常兜底 | P1 | ✅ |
| TC-S2D10-009 | 图表收集逻辑 | P1 | ✅ |
| TC-S2D10-010 | 历史消息传递 | P1 | ✅ |

### ToolRegistry

| 用例 ID | 名称 | 优先级 | 状态 |
|:--------|:-----|:------:|:----:|
| TC-S2D10-011 | Spring DI 收集 | P0 | ✅ |
| TC-S2D10-012 | 名称重复检测 | P0 | ✅ |
| TC-S2D10-013 | 正常分发执行 | P0 | ✅ |
| TC-S2D10-014 | 空 toolName 检查 | P1 | ✅ |
| TC-S2D10-015 | 未知工具处理 | P1 | ✅ |
| TC-S2D10-016 | 执行异常捕获 | P1 | ✅ |
| TC-S2D10-017 | 工具定义格式 | P0 | ✅ |

### AgentPrompt + Tool + SSE

| 用例 ID | 名称 | 优先级 | 状态 |
|:--------|:-----|:------:|:----:|
| TC-S2D10-018 | 动态时间注入 | P0 | ✅ |
| TC-S2D10-019 | 角色声明 | P0 | ✅ |
| TC-S2D10-020 | 10 条规则完整性 | P0 | ✅ |
| TC-S2D10-021 | 输出格式约束 | P1 | ✅ |
| TC-S2D10-022 | query_load 时间范围 | P0 | ✅ |
| TC-S2D10-023 | query_load 等间隔采样 | P1 | ✅ |
| TC-S2D10-024 | get_stats 实时 | P0 | ✅ |
| TC-S2D10-025 | get_stats 区间统计 | P0 | ✅ |
| TC-S2D10-026 | query_forecast 缓存 | P0 | ✅ |
| TC-S2D10-027 | query_forecast 自动触发 | P0 | ✅ |
| TC-S2D10-028 | SSE 事件流完整性 | P0 | ✅ |
| TC-S2D10-029 | SSE 异常事件 | P1 | ✅ |
| TC-S2D10-030 | SSE 超时处理 | P2 | ✅ |
| TC-S2D10-031 | SSE Markdown 格式 | P1 | ✅ |
| TC-S2D10-032 | 会话列表 | P1 | ✅ |
| TC-S2D10-033 | 历史消息 | P1 | ✅ |
| TC-S2D10-034 | 图表历史恢复 | P1 | ✅ |

> **Day 10 用例总计**：**34 条**（AgentCore 10 + ToolRegistry 7 + Prompt 4 + Tool 执行 6 + SSE 4 + 对话记录 3）

---

> 📎 **下一步**：Day 11 测试 → [Sprint2-Day11-测试报告.md](./Sprint2-Day11-测试报告.md)
