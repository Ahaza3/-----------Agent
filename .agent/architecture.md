# 架构决策记录

> 项目记忆 · 关键架构决策及理由

---

## ADR-001：单模块 Spring Boot 而非 Maven 多模块

**决策日期**：2026-07-10  
**状态**：✅ 已确认

**背景**：传统企业项目常按 `controller / service / mapper / common` 拆分为 Maven 多模块。

**决策**：使用 **单模块 Spring Boot**，一个 `pom.xml` 打包一个 Jar。

**理由**：
- 15 天项目体量小（预计 < 50 个 Java 文件），多模块管理成本 > 收益
- 避免 IDE 导入问题、构建顺序调试、模块间依赖管理
- Dockerfile 多阶段构建更简单（一个 Jar 即可）

---

## ADR-002：Python Flask 微服务作为 ML 推理主力方案

**决策日期**：2026-07-10  
**状态**：✅ 已确认（DJL 备选）

**背景**：模型用 Python PyTorch 训练，Java 端需在线推理。

**决策**：**Flask 微服务**（`ml/app.py`，~60 行）作为主力推理方案，Java 通过 OkHttp 调用。DJL 仅在环境确认可跑通时作为备选。

**理由**：
- DJL 在 Windows / Mac M 系列环境常有 `UnsatisfiedLinkError`，15 天赌不起
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
| 务实优先 | 15 天可交付 > 架构完美 |
| 核心链路优先 | Day 7 Sprint 1 结束前数据→展示必须贯通 |
| 有备选方案 | Flask 主力 / DJL 备选；LSTM 加分 / Prophet 兜底 |
| 模块化 | 前后端分离 + 后端分层，松耦合独立开发 |
| 可演示 | P0 功能 100% 可跑，P1/P2 有余力再加 |
