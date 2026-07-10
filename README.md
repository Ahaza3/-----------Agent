# ⚡ 电力负荷预测与智能告警 Agent

> **暑期实训项目** · 15 天完整开发流程  
> **技术栈**：Spring Boot 3 + React + MyBatis-Plus + MySQL + ECharts + LLM Agent  
> **团队规模**：4 人

---

## 📋 项目简介

本项目构建一个集 **负荷预测、异常检测、智能告警** 于一体的 AI Agent 系统。电力调度员可通过可视化大屏实时监控电网负荷、查看 AI 预测趋势、接收智能告警，并通过自然语言与系统交互查询数据。

**核心指标**：24h 负荷预测 MAPE < 5% ｜ 告警延迟 < 5s ｜ 异常检测准确率 > 90%

---

## 📁 文档导航

| 文档 | 内容 | 状态 |
|:-----|:-----|:----:|
| [需求规格说明书](./docs/01-需求规格说明书.md) | 用户故事、功能清单、用例图、数据流图 | ✅ v1.1 |
| [系统架构设计](./docs/02-系统架构设计.md) | 分层架构、模块设计、数据库设计、API 设计、部署架构 | ✅ v2.2 |
| [技术选型方案](./docs/03-技术选型方案.md) | 技术栈对比、选型理由、依赖清单、项目结构 | ✅ v2.2 |
| [项目开发计划](./docs/00-项目开发计划.md) | 15 天甘特图、每日任务拆解、风险预案 | ✅ v1.1 |

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
├── docker-compose.yml            # 一键部署（Day 13）
├── .github/workflows/            # GitHub Actions CI/CD
└── docs/                         # 项目文档
```

---

## 🚀 快速开始（Day 3-4 搭建完成后）

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

| 角色 | 姓名 | 核心职责 |
|:-----|:-----|:---------|
| 🎯 **项目经理** | — | 需求管理、进度把控、文档编写、PPT 答辩 |
| 🔧 **技术经理** | — | 架构设计、后端开发、数据库设计、ML 集成、Agent 开发 |
| 🎨 **产品经理** | — | 产品设计、UI/UX 原型、前端开发、可视化大屏 |
| 🧪 **测试工程师** | — | 测试用例、CI/CD 流水线、Docker 部署、代码 Review |

> 4 人紧凑团队，每个人都是关键角色。后端 & ML 归技术经理，前端 & 设计归产品经理，DevOps & 质量归测试工程师。

---

## 📊 15 天开发计划

| 阶段 | 时间 | 核心任务 | 交付物 |
|:-----|:-----|:---------|:-------|
| 📋 需求与设计 | Day 1 – 2 | 需求文档、架构设计、技术选型 | 4 份设计文档 ✅ |
| 🏗️ 基础搭建 | Day 3 – 4 | 项目脚手架、数据库建表、CI/CD 骨架 | 可运行的空项目 |
| ⚙️ 核心开发 | Day 5 – 9 | 数据管道、预测模型、大屏图表、Agent | 核心功能模块 |
| 🔗 集成联调 | Day 10 – 12 | 全链路联调、测试、修复、打磨 | 可演示的完整系统 |
| 🚀 部署答辩 | Day 13 – 15 | Docker 部署、PPT、演示排练、答辩 | 部署包 + 答辩材料 |

---

> **当前进度**：🟢 Day 1 完成 ✅ ｜ Day 2 进行中 →
