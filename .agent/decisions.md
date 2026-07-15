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

## 2026-07-12 · Day 3

### 决策 011：TBD-01 已解决 — LSTM 主力，Prophet 兜底

- **决策**：LSTM 作为主力推理模型（Flask 优先加载 TorchScript），Prophet 作为模型加载失败时的 fallback
- **实测**：LSTM MAPE 3.53% vs Prophet 4.71%（低 25%）；MAE 27.24 vs 36.61 MW
- **影响**：ADR-012 记录完整决策；Flask app.py 实现 LSTM→Prophet 自动降级

### 决策 012：TBD-03 已解决 — Zustand 状态管理

- **决策**：前端全局状态使用 Zustand（非 Redux / Context），按领域拆分 Store（`useDashboardStore`）
- **理由**：Zustand API 简洁（`create` 一个函数搞定），无 Provider 嵌套，与 React 18 兼容最佳
- **影响**：`stores/useDashboardStore.ts` 管理 loadData/predictions/alerts/stats 四个领域

---

## 2026-07-13 · Day 4

### 决策 013：AI Code Review SOP 落地

- **决策**：制定 [07-AI代码审查SOP.md](../docs/07-AI代码审查SOP.md)，所有 PR 必须走 AI 审查 → 人工确认 → Merge 流程
- **理由**：对应 30% 考核权重；禁飞区特殊规则（AI 只审查不给修复代码）
- **影响**：PR 模板增加禁飞区声明、Review 指令字段；GitHub 配置分支保护

### 决策 014：Sprint 1 超前交付

- **决策**：Day 4 完成 Day 4-7 约 85% 工作量（脚手架→数据管道→预测模型→可视化大屏全链路贯通）
- **影响**：Day 5-7 可用于 Sprint 1 收尾（Bug 注入练习 + 评审）和 Sprint 2 提前准备

### 决策 015：AI 协作审计机制建立

- **决策**：每个 Sprint 结束前执行 AI 协作审计（三层次范式合规检查），产出 [08-AI协作审计报告.md](../docs/08-AI协作审计报告.md)
- **Sprint 1 审计结果**：整体合规率 94%，禁飞区零违规，发现 2 个 AI 生成 Bug（SLF4J 格式符 + Flask 假数据）
- **影响**：审计成为 Sprint 评审标准流程；ADR-015 记录完整审计维度

### 决策 016：Sprint 1 技术笔记产出

- **决策**：整理 [09-Sprint1技术笔记.md](../docs/09-Sprint1技术笔记.md)（27 个踩坑记录 + 5 条核心经验 + 10 个 Bug 全记录）
- **理由**：经验沉淀是实训核心目标之一，避免 Sprint 2/3 重复踩坑
- **影响**：7 条 Sprint 2 预防措施；测试文档重编号（09→10-13，为技术笔记腾位）

### 决策 017：Brutalist CRT Terminal 设计风格

- **决策**：前端大屏采用 CRT 终端风格（零圆角/零阴影/终端配色/等宽字体），Ant Design 5 Theme Token 覆盖 15 个组件
- **理由**：契合电力调度监控场景；统一设计语言降低后续页面决策成本
- **影响**：ADR-013 记录；`theme/tokens.ts` 172 行完整 Token 覆盖

---

## 2026-07-14 · Day 5

### 决策 018：文档重编号方案

- **决策**：测试报告 09-12 → 10-13，腾出 09 号位存放 Sprint 1 技术笔记
- **影响**：docs/ 编号规范写入 conventions.md；后续文档按新编号追加

---

## 待决策事项

| 编号 | 事项 | 讨论时间 | 负责人 |
|:-----|:-----|:---------|:-------|
| TBD-02 | DeepSeek vs 通义千问 LLM API 锁定 | Sprint 2 (Day 9) | 技术经理 |
| TBD-04 | 是否接入真实天气数据 API | Sprint 2 | 全员 |

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
