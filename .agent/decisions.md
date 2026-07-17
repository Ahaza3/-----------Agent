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

## 2026-07-15 · Day 6

### 决策 019：前后端时间参数统一规范

- **决策**：前后端时间传参统一使用毫秒级时间戳（`long`），Java `LocalDateTime` → `Instant.toEpochMilli()`，前端 `dayjs().valueOf()`
- **理由**：Day 5 时间范围选择器联调暴露了格式不统一问题（ISO 8601 vs 毫秒戳 vs `yyyy-MM-dd HH:mm:ss`），导致跨天/跨月边界数据查询异常
- **影响**：Controller 层参数类型统一为 `Long`；前端 api 封装层统一 `dayjs` 毫秒戳入参；规避时区与格式转换问题

### 决策 020：ML 环境一键初始化脚本

- **决策**：编写 `ml/setup.sh`（76 行），5 步完成环境全流程：创建 Python venv → 生成模拟数据 → 导入 MySQL → 特征工程 → 训练 LSTM + Prophet
- **理由**：新成员手动搭 ML 环境需 2-3 小时（Python 依赖、数据生成、特征提取、模型训练），一键脚本压到 ~10 分钟
- **影响**：`ml/setup.sh` 成为环境初始化的唯一入口；readme 和 CLAUDE.md 均引用此脚本；训练流程可复现

### 决策 021：TorchScript 静态图适配方案

- **决策**：固定 LSTM 输入序列长度（`seq_length=168`），对输入数据做 padding 对齐，适配 TorchScript 静态图约束
- **理由**：TorchScript 导出要求固定输入 shape，而原始 PyTorch 模型支持动态长度 → 导出后 Flask 加载报 shape mismatch
- **影响**：`train_lstm.py` 增加序列长度参数；Flask `app.py` 推理前对输入自动 padding/truncate；模型元数据 `lstm_meta.pt` 记录 `seq_length`

---

## 2026-07-16 · Day 7

### 决策 022：Sprint 1 评审会议流程设计

- **决策**：90 分钟 9 环节评审流程：Sprint 目标回顾(5) → 系统演示(25) → 验收逐项确认(15) → 禁飞区代码检查(15) → Bug 注入练习(15) → 回顾改进(10) → Sprint 2 规划(5)
- **理由**：实训要求结构化评审，覆盖 4 项评分维度（演示/验收标准/禁飞区/Bug 注入）；Act 1-4 演示脚本覆盖全链路（数据→ML→API→大屏→工程资产）
- **影响**：[10-Sprint1评审会议议程](../docs/10-Sprint1评审会议议程.md) 完整设计；M1 验收报告同步产出

### 决策 023：Bug 注入评测练习机制建立

- **决策**：Sprint 1 评审阶段建立 Bug 注入练习流程：评审者注入 3 个 Bug（⭐~⭐⭐⭐ 难度梯度）→ 团队 30 分钟定位 + 修复 → 回顾总结
- **理由**：实训"Bug 注入评测"是正式考核环节，需提前演练熟悉流程；Sprint 1 先练习轮（非正式评分），Sprint 2/3 正式评测
- **影响**：3 个已知 Bug（B4/B5/B10）记录在案作为复习素材；练习 Bug 覆盖 Controller/前端图表/Service 缓存三类

### 决策 024：M1 全链路 5 节点逐层验证方法

- **决策**：M1 里程碑验收采用 5 节点逐层验证法 — MySQL(:3306) → ML 模型文件 → Spring Boot(:8080) → Flask(:5000) → React(:5173)，每节点静态代码检查 + 运行时检查双轨
- **理由**：全链路 5 节点分离部署，问题定位需逐层排除；静态检查在未启动服务时即可发现 80% 问题
- **影响**：[11-M1里程碑验收报告](../docs/11-M1里程碑验收报告.md) 产出；验证方法成为后续 M2/M3 验收模板

### 决策 025：模拟负荷特征模型统一化

- **决策**：重构 `generate_mock_data.py`，统一为双峰负荷模型（工业上午峰 + 居民晚间峰），加入偏移衰减衔接（消除分段间断点）+ 确定性噪声（日期哈希种子，可复现）
- **理由**：旧版 mock 数据分段拼接存在断点（跨段跳变 ~50 MW），前端曲线图出现垂直断线；确定性噪声保证每次生成数据一致，方便测试验证
- **影响**：`generate_mock_data.py` 重写核心生成逻辑；`feature_engineering.py` 配合调整特征计算；所有历史数据需重新生成

### 决策 026：Sprint 2 范围与风险预案确认

- **决策**：Sprint 2（Day 9-11）范围锁定为告警检测引擎 + 智能 Agent + 全链路贯通；关键风险预案：(1) LLM API 不稳定 → Agent 降级为规则查询+固定回复；(2) 禁飞区手写进度慢 → Day 8 提前准备 NFZ 框架代码；(3) WebSocket 调试困难 → Postman 独立测试先行
- **影响**：Day 9 告警 NFZ-3 手写优先；Day 10 Agent NFZ-1/2 手写为核心；Day 11 Bug 注入正式评测；LLM 选型（TBD-02）Day 9 前锁定

---

## 2026-07-17 · Day 8（Sprint 1 评审日）

### 决策 027：M1 验收结论 — 全链路贯通，9/9 全绿通过

- **决策**：M1 里程碑 9 项验收标准全部达标（🟢），全链路 5 节点 MySQL/ML/Spring/Flask/React 全部运行中，0 个 🔴 阻断 Bug
- **理由**：M1 定义"数据 → API → 大屏曲线图全链路贯通"，Day 4-7 完成从脚手架到可视化大屏的端到端交付；实际提前 1 天（原计划 Day 7，实际 Day 7 完成验收）
- **影响**：M1 验收报告 [11-M1里程碑验收报告](../docs/11-M1里程碑验收报告.md) 定稿；已知缺陷 3 个（B4/B5/代码重复）记录在案，Sprint 2 修复；Sprint 2 可按计划 Day 9 启动

### 决策 028：Sprint 1 经验沉淀 — 5 项文档产出闭环

- **决策**：Sprint 1 结束时文档体系从 8 份扩展到 14 份（00-13），覆盖 5 项人工产出的 4/5（需求规格/架构设计/接口设计/数据模型已就绪，部署方案 Day 14 补齐）
- **影响**：`docs/` 目录完整编号；`.agent/` 三文件（architecture/conventions/decisions）持续同步；新成员 pull 后可独立上手

---

## 待决策事项

| 编号 | 事项 | 讨论时间 | 负责人 |
|:-----|:-----|:---------|:-------|
| TBD-02 | DeepSeek vs 通义千问 LLM API 锁定 | Sprint 2 Day 9 | 技术经理 |
| TBD-04 | 是否接入真实天气数据 API | Sprint 2 | 全员 |
| TBD-05 | Sprint 2 代码重复消除策略（`quickToRange` / `_engineer_features`） | Sprint 2 Day 10 | 技术经理 |
| TBD-06 | 前端大组件拆分方案（Dashboard 317 行 → 子组件） | Sprint 2 Day 10 | 产品经理 |

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
