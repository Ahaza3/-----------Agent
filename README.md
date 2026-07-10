# 电力负荷预测与智能告警 Agent

> **暑期实训项目** | 15 天完整开发流程  
> **技术栈**: Spring Boot 3 + React + MyBatis-Plus + MySQL + ECharts + LLM Agent  
> **团队规模**: 4-5 人

---

## 📋 项目简介

本项目构建一个集**负荷预测、异常检测、智能告警**于一体的 AI Agent 系统。电力调度员可通过可视化大屏实时监控电网负荷、查看 AI 预测趋势、接收智能告警，并通过自然语言与系统交互查询数据。

---

## 📁 文档导航

| 文档 | 内容 | 状态 |
|------|------|------|
| [需求规格说明书](./docs/01-需求规格说明书.md) | 用户故事、功能清单、用例图、数据流图 | ✅ v1.0 |
| [系统架构设计](./docs/02-系统架构设计.md) | 分层架构、模块设计、数据库设计、API 设计、部署架构 | ✅ v1.0 |
| [技术选型方案](./docs/03-技术选型方案.md) | 技术栈对比、选型理由、依赖清单、项目结构 | ✅ v1.0 |

---

## 🏗️ 项目结构（规划）

```
power-load-agent/
├── frontend/                # React + TypeScript + ECharts
│   ├── src/
│   │   ├── components/      # 通用组件
│   │   ├── pages/           # 页面（大屏/告警中心/Agent对话/管理后台）
│   │   ├── hooks/           # 自定义 Hooks
│   │   ├── stores/          # Zustand 状态管理
│   │   ├── services/        # API 调用封装
│   │   └── types/           # TypeScript 类型定义
│   └── package.json
├── backend/                 # Spring Boot 3 (单模块)
│   ├── src/main/java/com/example/
│   │   ├── controller/      # 控制器层
│   │   ├── service/         # 业务逻辑层
│   │   ├── mapper/          # MyBatis-Plus Mapper
│   │   ├── entity/          # 实体类
│   │   ├── dto/             # 数据传输对象
│   │   ├── agent/           # LLM Agent 模块（自研轻量）
│   │   ├── ml/              # ML 推理集成（调用 Python 微服务）
│   │   ├── alert/           # 告警检测引擎
│   │   ├── common/          # 通用组件（R<T>/异常/常量）
│   │   ├── config/          # Spring 配置
│   │   └── websocket/       # WebSocket 推送
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/    # Flyway SQL 脚本
├── ml/                      # Python ML 训练脚本 + 推理服务
│   ├── app.py               # Flask 推理微服务
│   ├── train_lstm.py
│   ├── train_prophet.py
│   └── requirements.txt
├── frontend/                # React + TypeScript + ECharts
├── docker-compose.yml       # 一键部署
├── .github/workflows/       # CI/CD
└── docs/                    # 文档
```

---

## 🚀 快速开始（Day 3-4 搭建完成后）

```bash
# 1. 克隆项目
git clone <repo-url>
cd power-load-agent

# 2. 复制环境变量
cp .env.example .env

# 3. 一键启动
docker-compose up -d

# 4. 访问
# 大屏: http://localhost
# API文档: http://localhost/api/doc.html  (Knife4j 增强 Swagger)
```

---

## 👥 团队分工

| 角色 | 职责 | 技能要求 |
|------|------|----------|
| PM/架构师 | 需求管理、架构设计、进度把控、文档 | 统筹能力 |
| 后端工程师 | REST API、数据库、缓存、WebSocket | Java, Spring Boot, MyBatis-Plus, MySQL |
| 算法工程师 | 数据管道、Python训练脚本、DJL推理集成 | PyTorch, Pandas, DJL |
| 前端工程师 | 可视化大屏、管理后台、实时推送 | React, ECharts, WebSocket |
| Agent/DevOps | LLM集成、CI/CD、Docker部署 | LLM API, OkHttp, Docker, GitHub Actions |

---

## 📊 15 天开发计划

| 阶段 | 天数 | 核心任务 |
|------|------|----------|
| 需求与设计 | Day 1-2 | 需求文档、架构设计、技术选型 ✅ (当前) |
| 基础搭建 | Day 3-4 | 项目脚手架、数据库、CI/CD |
| 核心开发 | Day 5-9 | 数据管道、预测模型、大屏、Agent |
| 集成测试 | Day 10-12 | 联调、测试、优化 |
| 部署答辩 | Day 13-15 | Docker部署、文档、PPT |

---

> **当前进度**: Day 1 完成 ✅ | Day 2 进行中 →
