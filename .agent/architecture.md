# 架构决策记录

> 项目记忆 · 关键架构决策及理由

---

## ADR-001：单模块 Spring Boot 而非 Maven 多模块

**决策日期**：2026-07-10  
**状态**：✅ 已确认

**背景**：传统企业项目常按 `controller / service / mapper / common` 拆分为 Maven 多模块。

**决策**：使用 **单模块 Spring Boot**，一个 `pom.xml` 打包一个 Jar。

**理由**：
- 16 天项目体量小（预计 < 50 个 Java 文件），多模块管理成本 > 收益
- 避免 IDE 导入问题、构建顺序调试、模块间依赖管理
- Dockerfile 多阶段构建更简单（一个 Jar 即可）

---

## ADR-002：Python Flask 微服务作为 ML 推理主力方案

**决策日期**：2026-07-10  
**状态**：✅ 已确认（DJL 备选）

**背景**：模型用 Python PyTorch 训练，Java 端需在线推理。

**决策**：**Flask 微服务**（`ml/app.py`，~60 行）作为主力推理方案，Java 通过 OkHttp 调用。DJL 仅在环境确认可跑通时作为备选。

**理由**：
- DJL 在 Windows / Mac M 系列环境常有 `UnsatisfiedLinkError`，16 天赌不起
- Flask 服务独立测试（`curl` 一把梭），问题定位迅速
- 训练脚本和推理服务共用 Python 生态，无需模型格式转换

---

## ADR-003：自研 Agent 框架而非 LangChain4j / Spring AI

**决策日期**：2026-07-10  
**状态**：✅ 已确认

**背景**：LLM Function Calling 是 Agent 模块核心技术。

**决策**：**自研轻量 Agent**（OkHttp + SSE，核心 ~200 行 Java），不引入 LangChain4j 或 Spring AI。

**理由**：
- Function Calling 核心逻辑仅需 150-200 行代码，框架学习成本 > 自研成本
- 自研代码完全白盒，答辩时可逐行讲解（对应 NFZ-1 禁飞区）
- 零外部 Agent 依赖，Docker 镜像更轻

---

## ADR-004：告警文案使用固定模板而非 LLM 生成

**决策日期**：2026-07-10  
**状态**：✅ 已确认

**背景**：告警事件需要附带分析文案和建议。

**决策**：使用 **AlertTemplate 固定字符串模板**（`String.format`），不调用 LLM 生成告警文案。

**理由**：
- LLM 调用 2-5 秒延迟 + 网络依赖，固定模板毫秒级返回且 100% 可用
- 告警延迟 < 5s 的性能指标无法用 LLM 满足
- 对应 NFZ-3 禁飞区——手写模板逻辑，确保理解每个分支

---

## ADR-005：前端 ECharts 而非 AntV / D3.js

**决策日期**：2026-07-10  
**状态**：✅ 已确认

**决策**：可视化库使用 Apache ECharts。

**理由**：
- 对电力大屏类时序图表支持最完善（`dataset` 模式 + `dataZoom` 时间拖拽）
- 中文文档完善，`echarts-for-react` 封装成熟
- 大屏自适应布局（1920×1080）开箱即用

---

## ADR-006：AI 禁飞区划定

**决策日期**：2026-07-10  
**状态**：✅ 已确认

**决策**：3 个核心功能划为 AI 禁飞区，代码必须手写，答辩日逐行讲解。

| 编号 | 模块 | 理由 |
|:-----|:-----|:-----|
| NFZ-1 | Agent 核心循环 | Agent 最核心的 Function Calling 编排逻辑，必须完全掌握 |
| NFZ-2 | Prompt 工程 | System Prompt 设计决定 Agent 行为边界，不能依赖 AI 自动生成 |
| NFZ-3 | 告警检测引擎 | 核心业务逻辑（分级 + 模板），Code Review 重点 |

---

## 架构原则速查

| 原则 | 含义 |
|:-----|:-----|
| 务实优先 | 16 天可交付 > 架构完美 |
| 核心链路优先 | Day 7 Sprint 1 结束前数据→展示必须贯通 |
| 有备选方案 | Flask 主力 / DJL 备选；LSTM 加分 / Prophet 兜底 |
| 模块化 | 前后端分离 + 后端分层，松耦合独立开发 |
| 可演示 | P0 功能 100% 可跑，P1/P2 有余力再加 |

---

## ADR-007：新增 conversation 表（V1 迁移）

**决策日期**：2026-07-11  
**状态**：✅ 已确认

**背景**：04-接口设计文档中 Agent 对话接口使用 `conversationId` 管理会话，01-需求规格说明书数据流图包含 D4 对话记录库，但 05-数据模型设计未定义 conversation 表。

**决策**：在 V1 Flyway 迁移中新增 `conversation` 表（id / conversation_id / role / content / tool_name / created_at），与其他 5 张 P0 表一同建表。

**理由**：Agent 对话是 P0 功能，conversationId 是 POST /api/v1/agent/chat 的请求参数。没有 conversation 表会导致对话历史无法存储，且 V3 追加迁移增加复杂度。

---

## ADR-008：告警模块 P0 文件清单

**决策日期**：2026-07-11  
**状态**：✅ 已确认

**背景**：各文档对 `alert/` 目录下文件列表描述不一致——02 文档有 PushService + TrendDetector，03 文档有 ThresholdDetector + AlertTemplate，NFZ-3 禁飞区要求手写。

**决策**：
- P0：`alert/ThresholdDetector.java` + `alert/AlertTemplate.java`（NFZ-3 手写）
- 砍掉：`alert/PushService.java` — 告警推送直接通过 AlertServiceImpl 注入 `SimpMessagingTemplate` 完成，与 `websocket/DashboardWebSocketHandler` 无职责重叠
- 延后 P1：`alert/TrendDetector.java`

---

## ADR-009：预测调度机制

**决策日期**：2026-07-11  
**状态**：✅ 已确认

**决策**：预测使用 **Spring `@Scheduled` 定时触发**（每小时一次），在 `PredictServiceImpl` 中实现。

**理由**：只需一个注解，零额外基础设施，不需要 API 端点或前端交互。比手动触发更易于实现。

---

## ADR-010：开发环境去 Docker 化

**决策日期**：2026-07-11  
**状态**：✅ 已确认

**背景**：Docker Desktop 未安装，但本地已有 MySQL 8.0 运行。

**决策**：开发阶段完全不用 Docker Compose——本地 MySQL `localhost:3306` 直连。`docker-compose.yml` 仅保留作为 Day 14 生产部署参考。

**影响**：
- `application-dev.yml` 排除 `RedisAutoConfiguration`（Redis 未安装）
- 密码统一为 `123456`
- `application-dev.yml` 连接参数 `characterEncoding=UTF-8`（不能用 `utf8mb4`，那是 MySQL 内部字符集名）

---

## ADR-011：模拟数据生成用 Python

**决策日期**：2026-07-11  
**状态**：✅ 已确认

**决策**：模拟数据由 `ml/generate_mock_data.py` 生成（双峰模型 + 噪声 + 季节趋势），不再用 Java MockDataGenerator。

**理由**：02-架构设计文档两处矛盾（3.1 节说 Java，3.2 节点说 Python）。Python 版本与后续特征工程、模型训练共用 pandas 生态，代码更简洁。

---

## ADR-012：LSTM 为主力预测模型，Prophet 为兜底

**决策日期**：2026-07-13  
**状态**：✅ 已确认（解决 TBD-01）

**背景**：Day 6 完成 Prophet 和 LSTM 两种模型训练，需确定上线策略。

**决策**：**LSTM 作为主力推理模型**（Flask 优先加载 TorchScript），Prophet 作为 fallback。

**实测数据**：

| 指标 | Prophet | LSTM |
|:-----|--------:|------:|
| MAPE | 4.71% | **3.53%** |
| MAE (MW) | 36.61 | **27.24** |
| RMSE (MW) | 45.22 | **37.46** |

**理由**：LSTM 三项指标全面优于 Prophet（MAPE 低 25%）。Prophet 保留作为模型文件损坏或 TorchScript 加载失败时的兜底。

---

## ADR-013：Brutalist CRT Terminal 前端设计风格

**决策日期**：2026-07-13  
**状态**：✅ 已确认

**背景**：Day 4-7 Sprint 1 前端开发中，需确定可视化大屏设计方向。

**决策**：采用 **Brutalist CRT Terminal** 风格——零圆角、零渐变、零阴影、终端配色（红 #FF2A2A / 黄 #E6C300 / 绿 #4AF626 / 白 #EAEAEA）、JetBrains Mono 等宽字体。

**理由**：
- 与电力调度中心终端监控的工业场景契合
- 深色主题在 1920×1080 大屏上可读性好
- 统一设计语言降低后续页面（告警中心、Agent 对话）的设计决策成本
- Ant Design 5 主题 Token 完整覆盖（Layout/Menu/Card/Table/Button/Input/Select/Tag/Tooltip/Statistic/Modal/Segmented/DatePicker/Pagination 共 15 个组件）

---

## ADR-014：TorchScript 模型导出 + 元数据分离

**决策日期**：2026-07-13  
**状态**：✅ 已确认

**背景**：PyTorch 模型需导出为 Flask 推理服务可加载的格式。

**决策**：使用 **TorchScript `torch.jit.script` + `torch.jit.freeze`**，元数据（seq_length / scaler / feature_cols）独立保存为 `.pkl` 文件。

**导出文件清单**：
- `models/lstm_scripted.pt` — TorchScript 计算图
- `models/lstm_meta.pt` — seq_length 等超参数
- `models/lstm_scaler_x.pkl` / `lstm_scaler.pkl` — 特征/目标 StandardScaler
- `models/lstm_feature_cols.pkl` — 19 个特征列名列表

**理由**：TorchScript 是 PyTorch 生产部署标准格式；元数据分离避免模型文件中嵌入 Python 对象导致的跨环境兼容问题。

---

## ADR-015：Sprint 1 AI 协作审计机制

**决策日期**：2026-07-13  
**状态**：✅ 已确认

**背景**：项目规范要求"所有 AI 生成代码必须经过 Code Review"，需要建立审计机制。

**决策**：每个 Sprint 结束前执行一次 **AI 协作审计**（三层次范式合规检查），产出审计报告。

**审计维度**：
- 逐文件分类：💡 启发式 / ✍️ 精确式 / 🔒 约束式 / 🚫 Vibe Coding
- 禁飞区安全检查（零违规确认）
- 跨文件 AI 特征识别（代码重复、格式错误、风格一致性）
- Bug 全记录 + 修复追踪

**Sprint 1 审计结果**：[08-AI协作审计报告.md](../docs/08-AI协作审计报告.md) — 整体合规率 94%，发现 2 个 AI 生成 Bug。
