# ⚡ 电力负荷预测与智能告警 Agent

> **暑期实训项目** · 16 天完整开发流程（Sprint × 3 + 答辩日）
> **技术栈**：Spring Boot 3 + React + MyBatis-Plus + MySQL + ECharts + LLM Agent
> **团队规模**：4 人（PO + SM + Developer + QA）

---

## 📋 项目简介

本项目构建一个集 **负荷预测、异常检测、智能告警** 于一体的 AI Agent 系统。电力调度员可通过可视化大屏实时监控电网负荷、查看 AI 预测趋势、接收智能告警，并通过自然语言与系统交互查询数据。

**核心指标**：24h 负荷预测 MAPE < 5% ｜ 告警延迟 < 5s ｜ 异常检测准确率 > 90%

---

## 📁 文档导航

| 文档 | 内容 | 状态 |
|:-----|:-----|:----:|
| [项目开发计划](./docs/00-项目开发计划.md) | 16 天 Sprint 甘特图、能力培养、AI 禁飞区、评测活动 | ✅ v2.0 |
| [需求规格说明书](./docs/01-需求规格说明书.md) | 用户故事、功能清单、用例图、数据流图 | ✅ v1.1 |
| [系统架构设计](./docs/02-系统架构设计.md) | 分层架构、模块设计、数据库设计、API 设计、部署架构 | ✅ v2.2 |
| [技术选型方案](./docs/03-技术选型方案.md) | 技术栈对比、选型理由、依赖清单、项目结构 | ✅ v2.2 |
| [接口设计文档](./docs/04-接口设计文档.md) | REST API、WebSocket、SSE 端点、错误码 📄 D2 | ✅ v1.0 |
| [数据模型设计](./docs/05-数据模型设计.md) | ER 图、DDL 建表语句、索引策略、数据字典 📄 D3 | ✅ v1.0 |
| [部署方案设计](./docs/06-部署方案设计.md) | Docker Compose、Dockerfile、Nginx、CI/CD 📄 D5 | ✅ v1.0 |

---

## 🏗️ 项目结构（规划）

```
power-load-agent/
├── frontend/                     # React + TypeScript + ECharts
│   ├── src/
│   │   ├── components/           # 通用组件（StatCard / AlertBadge / LoadChart）
│   │   ├── pages/                # 页面（大屏 / 告警中心 / Agent 对话 / 管理后台）
│   │   ├── hooks/                # 自定义 Hooks
│   │   ├── stores/               # Zustand 状态管理
│   │   ├── services/             # API 调用封装
│   │   └── types/                # TypeScript 类型定义
│   └── package.json
├── backend/                      # Spring Boot 3 单模块
│   ├── src/main/java/com/example/
│   │   ├── controller/           # REST 控制器
│   │   ├── service/              # 业务逻辑层（接口 + impl）
│   │   ├── mapper/               # MyBatis-Plus Mapper
│   │   ├── entity/               # 实体类（@TableName）
│   │   ├── dto/                  # 请求/响应 DTO
│   │   ├── agent/                # LLM Agent（AgentCore + ToolRegistry + Tools）
│   │   ├── ml/                   # ML 推理集成（调用 Python 微服务）
│   │   ├── alert/                # 告警检测引擎（ThresholdDetector + AlertTemplate）
│   │   ├── common/               # 通用组件（R<T> / 异常 / 常量）
│   │   ├── config/               # Spring 配置
│   │   └── websocket/            # WebSocket 实时推送
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/         # Flyway SQL 迁移脚本
├── ml/                           # Python ML 训练脚本 + 推理服务
│   ├── app.py                    # Flask 推理微服务（~60 行）
│   ├── train_lstm.py             # LSTM 训练
│   ├── train_prophet.py          # Prophet 基线训练
│   ├── generate_mock_data.py     # 模拟数据生成器
│   └── requirements.txt
├── docker-compose.yml            # 一键部署（Day 14）
├── .github/workflows/            # GitHub Actions CI/CD
├── docs/                         # 项目文档
└── .agent/                       # 项目记忆（AI 协作规范）
    ├── conventions.md             # 工程规范
    ├── architecture.md            # 架构决策
    └── decisions.md               # 关键决策记录
```

---

## 🚀 快速开始（Sprint 1 搭建完成后）

```bash
# 1. 克隆项目
git clone <repo-url> && cd power-load-agent

# 2. 配置环境变量
cp .env.example .env

# 3. 一键启动
docker-compose up -d

# 4. 访问
# 大屏仪表盘    → http://localhost
# API 文档      → http://localhost/api/doc.html  (Knife4j 增强 Swagger)
```

---

## 👥 团队分工

| Scrum 角色 | 岗位 | 姓名 | 核心职责 |
|:-----------|:-----|:-----|:---------|
| 🎯 **SM** | 项目经理 | — | 需求管理、进度把控、Sprint 主持、文档编写、PPT 答辩 |
| 🎨 **PO** | 产品经理 | — | 产品设计、UI/UX 原型、前端开发、可视化大屏 |
| 🔧 **Developer** | 技术经理 | — | 架构设计、后端开发、数据库、ML 集成、🚫 Agent 禁飞区手写 |
| 🧪 **QA** | 测试工程师 | — | 测试用例、CI/CD、Docker 部署、Bug 注入评测、代码 Review |

> **Scrum 协作**：SM 驱动迭代节奏，PO 定义需求优先级，Developer 负责技术实现（含 3 个禁飞区手写），QA 保障质量与评测应对。

---

## 📊 16 天开发计划

| 阶段 | 时间 | 核心任务 | 交付物 |
|:-----|:-----|:---------|:-------|
| 📋 方法论 & 选题 | Day 1 | 行业讲座、选题确认、Git/DevOps 规范学习 | 项目选题 + 团队分工 |
| 🎓 方向实战 | Day 2 – 3 | 分技术方向授课、翻转课堂、任务驱动学习 | 技术栈熟练、环境就绪 |
| 🔄 Sprint 1 | Day 4 – 7 | 脚手架 → 数据管道 → 预测模型 → 大屏骨架 | 全链路贯通 + Bug 注入练习 |
| ☕ 休息日 | Day 8 | 不排任务，恢复精力 | — |
| 🔄 Sprint 2 | Day 9 – 11 | 告警引擎 + Agent 开发（🚫 禁飞区核心） | 告警 + Agent + 集成 |
| 🔄 Sprint 3 | Day 12 – 15 | 测试 → 打磨 → 部署 → 答辩准备 | 部署包 + 答辩材料 |
| 🎤 答辩日 | Day 16 | 演示 + 即兴修改评测 + 禁飞区检查 + Q&A | 项目交付 |

---

## 🎯 能力培养 & 考核

| 能力维度 | 权重 | 考核方式 |
|:---------|:----:|:---------|
| 🔧 工程实践能力 | **30%** | AI 辅助完成全流程开发 |
| 🔍 AI 代码审查能力 | **30%** | Bug 注入评测 + 即兴修改 |
| 📏 工程规范能力 | **20%** | 设计文档 + Git 规范 + 测试覆盖率 |
| 🤝 团队协作能力 | **20%** | Scrum 迭代 + Code Review |

## 🚫 AI 禁飞区（3 个核心功能必须手写）

| 禁飞区 | 模块 | 考核方式 |
|:-------|:-----|:---------|
| 🚫 NFZ-1 | Agent 核心循环（`AgentCore.java`） | 逐行讲解 Function Calling 编排 |
| 🚫 NFZ-2 | Prompt 工程（System Prompt + Tool Schema） | 讲解设计思路 |
| 🚫 NFZ-3 | 告警检测引擎（`ThresholdDetector` + `AlertTemplate`） | 讲解分级逻辑 |

> ⚡ 答辩日现场抽查，无法讲解 → 该模块分数清零

## 🧪 三大评测活动

| 评测 | 规则 | 时限 |
|:-----|:-----|:----:|
| 🐛 Bug 注入 | 每 Sprint 注入 3 个 Bug | 30 min |
| ⚡ 即兴修改 | 答辩日现场需求变更 | 30 min |
| 🔍 禁飞区检查 | 逐行讲解 3 个手写模块 | — |

---

> **当前进度**：已完成 Sprint 2/3 核心功能联调与缺陷修复，当前分支：`codex/fix-agent-dashboard-ticket-fixes`。

## 当前实现同步（2026-07-19）

项目已经进入前后端联调和缺陷修复阶段，当前实现与最初设计相比有以下关键补充：

- **Agent 聊天**：`POST /api/v1/agent/chat` 使用 SSE，事件顺序为 `thinking → text → chart → done`。后端一次性发送完整 Markdown 文本，避免按行拆分导致前端刷新前无法正确渲染；助手消息的 ECharts 配置会持久化到 `conversation.chart_option`，历史会话刷新后可恢复图表。
- **大屏曲线**：历史负荷、恢复段、实时负荷拆分为独立 ECharts series。恢复段到实时段之间只生成前端视觉 bridge 曲线，使用 15 分钟插值和平滑连接；该 bridge 不写回数据库，不影响未来数据按真实整点归档。
- **告警研判**：实时告警文案仍由 `AlertTemplate` 固定模板生成，保证告警触发链路低延迟；工单详情里的“AI 智能研判”会调用 LLM 生成调度/运维建议，失败时降级为规则研判。
- **红色告警建单**：红色告警不再自动提交正式工单。调度员侧会弹出“待确认工单草稿”，预填告警、AI 研判和调度建议；只有调度员点击“提交工单”后才正式创建工单。
- **工单时间线**：未创建工单的告警不会展示处置时间线，切换告警/工单时会清空旧 actions 状态，避免复用上一个工单的时间线。
- **模型管理**：运维角色可查看模型版本、训练 MAPE/RMSE、Flask 推理服务状态和当前实际推理模型；系统会登记 `ml/models` 下已有的 LSTM/Prophet 产物，并支持手动触发后台重训练。版本切换和重训练完成不会自动重载 Flask 模型文件。
