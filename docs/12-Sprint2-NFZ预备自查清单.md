# 🚫 Sprint 2 禁飞区（NFZ）预备自查清单

> **目标读者**：技术经理（Dev）  
> **用途**：Sprint 2 评审前逐模块熟悉 NFZ-1/2/3 代码边界，确保答辩时能逐行讲解  
> **Sprint 2 评审日**：Day 11（2026-07-21）  
> **红线**：任一 NFZ 模块无法逐行讲解 → 该模块分数清零

---

## 一、Sprint 2 禁飞区时间线

| 时间 | 事件 | 要求 |
|:-----|:-----|:-----|
| Day 8 (7/17) | 本清单下发 | 技术经理开始熟悉现有代码 |
| Day 9 (7/19) | NFZ-3 告警引擎手写 | ThresholdDetector + AlertTemplate 已交付，需能逐行讲解 |
| Day 10 (7/20) | NFZ-1/2 Agent 手写 | AgentCore + Prompt + Tool 体系已交付，需能逐行讲解 |
| Day 11 (7/21) | Sprint 2 评审 — NFZ 检查 | 15 分钟，三个模块逐一讲解（每个 5 分钟） |

---

## 二、NFZ-1：Agent 核心循环

### 2.1 代码边界

```
NFZ-1 手写范围（必须能逐行讲解）
├── AgentCore.java          172 行  ← 核心编排循环
├── Tool.java                25 行  ← Tool 接口定义
└── ToolRegistry.java        86 行  ← 工具注册与分发

非 NFZ（Agent 体系外围，理解即可）
├── AgentService.java               ← Spring SSE 流式服务层
├── AgentMessage.java               ← 消息 POJO（system/user/assistant/tool）
├── LlmClient.java                  ← LLM 调用接口
├── OpenAiCompatibleLlmClient.java  ← OpenAI 兼容实现（OkHttp）
├── MockLlmClient.java              ← 测试用 Mock
├── ToolResult.java                 ← 工具返回值 POJO
└── AgentPrompt.java                ← 归入 NFZ-2
```

### 2.2 AgentCore 逐层讲解提纲

```
第 1 层：入口校验（L42–48）
  - 空消息拦截 → AgentResponse.error("消息不能为空")
  - 超长消息截断 → 2000 字符硬限制
  - 为什么需要截断？（防止 LLM context window 溢出 + token 成本控制）

第 2 层：消息组装（L50–55）
  - System Prompt 来自 AgentPrompt.systemPrompt()（NFZ-2）
  - 历史消息从 AgentService 传入（conversation 表读取）
  - 用户消息追加在最后
  - 为什么要保留 history？（多轮对话上下文连续性）

第 3 层：主循环 runLoop()（L70–134）
  - 轮次上限 MAX_TOOL_ROUNDS=3 → 防止无限循环
  - 每轮最多 MAX_TOOLS_PER_ROUND=3 个工具调用
  - LLM 返回文本 → 直接返回 AgentResponse.text(content)
  - LLM 返回 tool_calls → 分发执行 → 递归下一轮
  - 工具执行结果的图表收集逻辑（chartOption 取第一个非空）

第 4 层：工具分发（L103–128）
  - 从 toolCall 中解析 function.name + function.arguments
  - toolRegistry.execute(toolName, argumentsJson) 分发
  - 工具结果序列化为 JSON → 追加 tool role 消息
  - 结果截断 MAX_TOOL_RESULT_JSON_LENGTH=4000 → 防止 token 爆炸

第 5 层：异常处理（L61–67）
  - LlmException → 返回友好错误
  - 通用 Exception → 兜底
  - 为什么不吞异常？（让前端能区分 LLM 故障 vs 业务异常）
```

### 2.3 ToolRegistry 逐层讲解提纲

```
第 1 层：Spring DI 自动收集（L23–34）
  - 构造函数接收 List<Tool> → Spring 自动注入所有 Tool Bean
  - 工具名重复检测 → 启动时 IllegalStateException 阻止启动
  - 为什么用构造注入而非 @Autowired 字段？（不可变性 + 测试友好）

第 2 层：getToolDefinitions()（L42–55）
  - 遍历所有 Tool → 组装 OpenAI tool function 定义
  - type: "function" → function.name/description/parameters
  - 为什么返回 List<Map> 而非强类型？（LLM API 用 JSON，动态结构更灵活）

第 3 层：execute() 分发（L58–75）
  - 空 toolName 检查 → "工具名不能为空"
  - 未知工具检查 → "未知工具: xxx，可用工具: [...]"
  - 执行异常捕获 → ToolResult.fail() 而非抛出
  - 为什么异常不向上抛？（让 LLM 看到错误后自行修正或告知用户）
```

### 2.4 Tool 接口边界

```java
public interface Tool {
    String name();                          // 全局唯一的工具名
    String description();                   // 给 LLM 看的功能描述
    Map<String, Object> parameters();       // OpenAI function parameters JSON Schema
    ToolResult execute(String argumentsJson); // 执行工具
}
```

**边界说明**：
- `name()` 必须在 ToolRegistry 中唯一，重复则启动报错
- `description()` 是 Prompt 工程的一部分（NFZ-2），影响 LLM 的工具选择准确率
- `parameters()` 返回 JSON Schema — 不是手写 JSON 字符串，而是用 `LinkedHashMap` 构建（类型安全、IDE 可检查）
- `execute()` 接收 LLM 传的 JSON 参数字符串，返回 `ToolResult`（含 summary + data + chart）

---

## 三、NFZ-2：Prompt 工程 + Tool Definitions

### 3.1 代码边界

```
NFZ-2 手写范围（必须能逐行讲解）
├── AgentPrompt.java               54 行  ← System Prompt（角色+规则+格式约束）
├── QueryLoadTool.java            228 行  ← name/description/parameters + 执行逻辑
├── GetStatsTool.java             217 行  ← name/description/parameters + 执行逻辑
└── QueryForecastTool.java        195 行  ← name/description/parameters + 执行逻辑

非 NFZ（但需理解交互关系）
├── Tool.java          ← 接口定义（NFZ-1）
└── ToolRegistry.java  ← 注册分发（NFZ-1）
```

### 3.2 AgentPrompt 讲解提纲

```
第 1 层：动态时间注入（L18–21）
  - 每次请求调用 systemPrompt() → 动态取 Asia/Shanghai 当前时间
  - 为什么不在启动时 static 初始化？（Prompt 需要实时时间，LLM 据此判断"昨天""刚才"）

第 2 层：角色 & 能力声明（L22–23）
  - "电力负荷监控与智能告警助手" → 角色锚定
  - "通过调用工具来获取负荷数据" → 明确工具使用是唯一数据来源

第 3 层：10 条规则（L25–35）
  - 规则 1-2：强制工具调用（防止 LLM 凭记忆编造数据）
  - 规则 3：MOCK 数据声明（合规要求）
  - 规则 4-5：能力边界（不得编造设备故障/操作电网）
  - 规则 6：建议免责声明
  - 规则 7-10：时间、语言、错误处理、安全约束

第 4 层：输出格式规范（L37–48）
  - 结构层次：一句话结论 → 数据列表/表格 → 建议
  - Markdown 约束：只用 ##/###，不用 #；不用 --- 分割；不用 emoji
  - 数据格式：数字带单位、时间精确到分钟
  - MOCK 声明策略：只说一次，不要反复强调
  - 为什么约束这么细？（LLM 不加约束会输出冗长 JSON/代码块/过度装饰）
```

### 3.3 Tool Definitions 讲解提纲

**三个 Tool 的共性结构**：
```
name()          → 蛇形命名，LLM function_call 的 function.name
description()   → 40-60 字中文，描述功能+适用场景+数据来源
parameters()    → JSON Schema (type/required/properties)，每个字段含 type + description
execute()       → 解析参数 → 调用 Service → 构建 ToolResult（summary + data + chart）
```

**各 Tool 边界**：

| Tool | 数据来源 | 适用场景 | 图表 |
|:-----|:---------|:---------|:----:|
| `query_load` | `LoadDataService.queryRange()` → `load_data` 表 | "昨天负荷高峰"、"本周趋势" | ✅ 历史折线 |
| `get_stats` | `RealtimeLoadService` + `LoadDataService.getStats()` + `AlertEventService` | "当前负荷"、"本月峰值"、"最近告警" | ❌ |
| `query_forecast` | `PredictionResultMapper` → `prediction_result` 表，可触发 `PredictService.forecast()` | "明天负荷预测"、"未来峰值" | ✅ 预测虚线 |

**讲解要点**：
- `query_load` 的等间隔采样算法（sample() — 步长公式 `(n-1)/(k-1)`，非随机抽样）
- `get_stats` 的时间参数可选性（不传=仅实时，传了=统计区间）
- `query_forecast` 的自动触发预测逻辑（表空 → 调用 predictService.forecast() → 重查）

---

## 四、NFZ-3：告警检测引擎

### 4.1 代码边界

```
NFZ-3 手写范围（必须能逐行讲解）
├── ThresholdDetector.java     111 行  ← 告警分级检测引擎
└── AlertTemplate.java          70 行  ← 告警文案固定模板

外围代码（理解交互即可）
├── AlertEvent.java             Entity
├── AlertEventService.java      CRUD 服务
├── AlertServiceImpl.java       告警业务编排（调 NFZ-3）
└── AlertRule.java              Entity（规则配置）
```

### 4.2 ThresholdDetector 逐层讲解提纲

```
第 1 层：配置解析（L49–57 + L93–110）
  - configJson 是 String → parseConfig() 用 Jackson 解析为 Map
  - 四个配置项：threshold / redRatio / orangeRatio / yellowRatio
  - 默认值：redRatio=1.10, orangeRatio=1.00, yellowRatio=0.90
  - 为什么用 JSON String 而非强类型？（规则存储在 alert_rule 表的 config_json 字段，可动态修改）

第 2 层：三级告警判定（L60–72）
  - RED:    currentLoad > threshold × redRatio    (超标 10%)
  - ORANGE: currentLoad > threshold × orangeRatio  (触及上限)
  - YELLOW: currentLoad > threshold × yellowRatio  (接近上限 90%)
  - 为什么从 RED 开始判断？（取最严重级别，一次只返回一个级别）

第 3 层：冷却时间（L78–84）
  - getCoolingTime() 从 config 读取，默认 3600 秒
  - 用途：防止同一告警在冷却时间内重复触发
  - 在哪里使用？→ AlertServiceImpl 中调用，判断 lastAlertTime + coolingTime < now

第 4 层：边界处理
  - threshold <= 0 → 不判定（配置无效）
  - config 解析失败 → 返回 null（不误报）
  - 为什么 null 而非抛出异常？（告警非关键路径，配置错误不应阻断主流程）
```

### 4.3 AlertTemplate 逐层讲解提纲

```
第 1 层：设计决策（类 Javadoc L7–19）
  - 告警文案用固定模板而非 LLM → 零延迟、零成本、零幻觉
  - 三类文案：aiAnalysis（告警描述）+ suggestion（建议措施）

第 2 层：generateAnalysis()（L32–53）
  - 计算公式：usageRatio = current/threshold × 100%，exceedRatio = max(0, usageRatio-100)
  - RED 模板 → "超出安全上限 {X} MW 的 {Y}%…建议立即启动调峰预案"
  - ORANGE 模板 → "已达到安全上限…的 {Y}%…请密切关注"
  - YELLOW 模板 → "接近安全上限…的 {Y}%…建议加强监控"
  - 为什么用 String.format 而非 MessageFormat？（简单场景，format 足够）

第 3 层：generateSuggestion()（L59–68）
  - RED → 3 条措施（增出力 + 需求侧响应 + 通知调度长）
  - ORANGE → 3 条措施（监控 + 准备增发 + 通知调度员）
  - YELLOW → 3 条措施（监控 + 检查计划 + 保持通信）
  - 措施的递进关系：RED 是"立即执行"，ORANGE 是"准备执行"，YELLOW 是"加强关注"

第 4 层：为什么不用 LLM（核心论点）
  - 幻觉风险：LLM 可能编造不存在的设备、人员、操作流程
  - 延迟不可控：LLM API 耗时 2-30 秒，告警需要 < 5 秒
  - 成本不划算：每条告警都调用 LLM 没有额外价值
  - 模板可控：电网告警文案是标准化流程，不需要创造性
```

---

## 五、NFZ 边界汇总 — 一句话版

| 模块 | 一句话边界 | 手写文件数 | 总行数 |
|:-----|:-----|:--------:|:------:|
| NFZ-1 | Agent 核心编排循环 + 工具注册分发机制 | 3 | ~283 |
| NFZ-2 | System Prompt 规则 + 3 个 Tool 的 name/desc/params 定义 | 4 | ~694 |
| NFZ-3 | 三级告警分级算法 + 固定模板中文文案 | 2 | ~181 |
| **合计** | | **9** | **~1158** |

### 不属于 NFZ 的 Agent/Alert 代码（不要混淆）

| 文件 | 为什么不是 NFZ | 谁负责讲解 |
|:-----|:-----|:-----|
| `AgentService.java` | SSE 流式推送，是 Spring Web 层面，不是 Agent 核心 | Dev（理解即可） |
| `LlmClient.java` | HTTP 调用接口，LLM API 通信层 | Dev（理解即可） |
| `OpenAiCompatibleLlmClient.java` | OkHttp 实现，网络 I/O | Dev（理解即可） |
| `MockLlmClient.java` | 测试替身 | Dev（理解即可） |
| `AlertServiceImpl.java` | 告警业务编排（调 NFZ-3 + WebSocket 推送），不是检测算法本身 | Dev（理解即可） |
| `AlertEventService.java` | MyBatis-Plus CRUD，数据访问层 | Dev（理解即可） |

---

## 六、常见追问 & 应对

### Q1：AgentCore 为什么用递归而非 while 循环？
**答**：递归天然表达"每轮 LLM 调用 → 判断结果 → 继续或终止"，每轮状态通过 messages 列表累积传递。while 循环等价但递归更清晰地表达了"每轮是上一轮的延续"。硬上限 MAX_TOOL_ROUNDS=3 防止栈溢出。

### Q2：Tool parameters 为什么不直接用 JSON 文件定义？
**答**：用 Java `LinkedHashMap` 构建可以享受 IDE 类型检查、重构支持和编译期验证。JSON 文件需要额外解析且容易写错 key。Tool 数量 ≤ 5，用代码构建比文件管理更简单。

### Q3：NFZ-3 为什么不支持多级阈值（如分时段不同阈值）？
**答**：P0 阶段用单一安全阈值覆盖基本需求。多级阈值需要 `alert_rule` 表扩展 `time_range` 字段，在 `ThresholdDetector.detect()` 中增加时间匹配逻辑 → P1/Sprint 3 可扩展。

### Q4：如果 LLM 返回了不在 ToolRegistry 中的 function_name 怎么办？
**答**：`ToolRegistry.execute()` 返回 `ToolResult.fail("未知工具: xxx，可用工具: [...]")` → AgentCore 将其作为 tool role 消息追加 → 下一轮 LLM 看到错误信息后会自行修正或告知用户。

### Q5：AlertTemplate 的文案如果产品经理不满意怎么办？
**答**：直接改 `String.format` 模板字符串即可，无需重新训练/部署任何模型。这是用固定模板的核心优势之一。

---

## 七、自查确认清单

在 Sprint 2 评审日前，逐项确认：

- [ ] NFZ-1：能不看代码画出 `AgentCore.run()` → `runLoop()` 的调用流程图
- [ ] NFZ-1：能解释 `MAX_TOOL_ROUNDS=3` 的理由和边界
- [ ] NFZ-1：能讲解 `ToolRegistry` 的 Spring DI 收集 + 重复检测机制
- [ ] NFZ-2：能背出 AgentPrompt 的 10 条规则中的前 5 条
- [ ] NFZ-2：能对比 3 个 Tool 的适用场景和数据结构差异
- [ ] NFZ-2：能解释 `query_forecast` 的自动触发预测逻辑（表空 → 生成 → 重查）
- [ ] NFZ-3：能写出三级告警的数学判定公式
- [ ] NFZ-3：能讲清楚为什么告警文案用固定模板而非 LLM（4 个理由）
- [ ] NFZ-3：能说明冷却时间在 `AlertServiceImpl` 中的使用方式
- [ ] 全局：能清晰区分哪些文件是 NFZ 手写范围、哪些是外围代码

---

> **关联文档**：[10-Sprint1评审会议议程](./10-Sprint1评审会议议程.md) · [.agent/architecture.md](../.agent/architecture.md) · [.agent/decisions.md](../.agent/decisions.md)  
> **版本记录**
>
> | 版本 | 日期 | 变更 |
> |:-----|:-----|:-----|
> | v1.0 | 2026-07-17 | 初版：NFZ-1/2/3 代码边界 + 逐层讲解提纲 + 常见追问 + 自查清单 |
