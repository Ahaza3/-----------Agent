# 关键决策记录

> 项目记忆 · 每日关键决策与变更

---

## 2026-07-10 · Day 1

### 决策 001：确认技术选型

- **决策**：Spring Boot 3 + React + MyBatis-Plus + MySQL + ECharts + LLM Agent
- **理由**：团队 Java 方向，Spring Boot 生态最熟悉；React 配合 ECharts 做大屏可视化
- **影响**：后端单模块、前端 Vite + React 18

### 决策 002：确认团队角色（Scrum 4 人）

- **决策**：SM（项目经理）+ PO（产品经理）+ Developer（技术经理）+ QA（测试工程师）
- **理由**：实训要求 PO+SM+QA 角色，第 4 人为技术开发
- **影响**：技术经理承担禁飞区手写 + ML 集成；QA 负责评测应对

### 决策 003：确认 3 个 AI 禁飞区

- **决策**：NFZ-1 AgentCore、NFZ-2 Prompt 工程、NFZ-3 告警检测引擎
- **理由**：对应实训"AI 编程方向"禁飞区要求（Agent 核心循环 + Prompt 工程），第 3 个选核心业务逻辑
- **影响**：技术经理需保证这 3 个模块完全手写，答辩可逐行讲解

### 决策 004：确认文档基线

- **决策**：6 份设计文档（01-06）+ README 已完成 v2 版本，覆盖全部 5 项人工产出
- **理由**：实训要求"设计先行"，Day 1-2 完成架构设计（02）、需求规格（01）、技术选型（03），Day 2-3 补齐接口设计（04）、数据模型（05）、部署方案（06）
- **影响**：5 项人工产出全部有对应文档；后续按 Sprint 迭代更新，Day 16 交付最终版

---

## 2026-07-11 · Day 2

### 决策 005：文档交叉审查 — 37 个问题确认

- **决策**：完成 12 份设计文档全面审查，发现逻辑冲突 12、命名不统一 6、设计缺失 11、表述模糊 8，合计 37 个问题
- **影响**：5 个阻塞级问题已在脚手架搭建前确认并修正；其余问题记录在案，Sprint 过程中逐步修正

### 决策 006：新增 conversation 表

- **决策**：V1 Flyway 迁移增加 `conversation` 表（conversation_id / role / content / tool_name / created_at）
- **理由**：04-接口设计文档 Agent 对话使用 conversationId，但 05-数据模型设计缺此表 → Day 4 建表时一并创建
- **影响**：V1__init_schema.sql 从 5 张表增加到 6 张（P0 全部表）

### 决策 007：告警模块 P0 仅保留 NFZ-3 核心

- **决策**：`alert/ThresholdDetector.java` + `alert/AlertTemplate.java`（NFZ-3 手写），PushService 砍掉，TrendDetector 延后 P1
- **理由**：PushService 与 websocket/DashboardWebSocketHandler 职责重叠，AlertServiceImpl 直接用 SimpMessagingTemplate 推送即可

### 决策 008：预测调度选 @Scheduled

- **决策**：预测定时触发使用 Spring `@Scheduled`，每小时执行一次
- **理由**：只需一个注解，比手动触发 API 更简单，未依赖额外基础设施

### 决策 009：脚手架技术细节

- **包名**：`com.powerload`（非 `com.example`）
- **数据库密码**：`123456`
- **JDBC 连接**：`characterEncoding=UTF-8`（不能用 `utf8mb4`，那是 MySQL 内部名）
- **开发 Redis**：`application-dev.yml` 排除 `RedisAutoConfiguration`（本地未安装）
- **Maven**：本地未安装命令行 Maven，使用 VS Code Java 扩展直接启动
- **Node.js**：`C:\Program Files\nodejs\`，PowerShell 需 `& "C:\Program Files\nodejs\npm.cmd" run dev`
- **Docker**：未安装 Docker Desktop，本地 MySQL `localhost:3306` 直连

### 决策 010：文档已知问题修复

- **CLAUDE.md**：15天→16天（4 处）；数据库表清单加入 conversation；部署时间统一 Day 14
- **pom.xml**：补上遗漏的 `spring-boot-starter-actuator` 依赖

---

## 待决策事项

| 编号 | 事项 | 讨论时间 | 负责人 |
|:-----|:-----|:---------|:-------|
| TBD-01 | Prophet vs LSTM 模型上线选择 | Day 6 | 技术经理 |
| TBD-02 | DeepSeek vs 通义千问 LLM API 锁定 | Day 3 | 技术经理 |
| TBD-03 | 前端状态管理方案（Zustand vs Context） | Day 3 | 产品经理（PO） |
| TBD-04 | 是否接入真实天气数据 API | Day 5 | 全员 |

---

## 决策模板

```markdown
### 决策 XXX：标题

- **决策**：选了什么方案
- **替代方案**：考虑过什么
- **理由**：为什么这么选
- **影响**：对项目的影响
- **日期**：YYYY-MM-DD
- **决策人**：角色名
```
