# 🏁 Sprint 1 评审会议议程

> **日期**：2026-07-17 ｜ **时长**：90 分钟 ｜ **参与人**：全员（SM + PO + Dev + QA）
>
> **Sprint 周期**：Day 4 – Day 7（2026-07-13 ~ 2026-07-17）

---

## 一、会议流程（90 min）

| 时间段 | 环节 | 时长 | 负责人 |
|:-------|:-----|:----:|:-------|
| 09:00 – 09:05 | Sprint 目标回顾 | 5 min | 项目经理（SM） |
| 09:05 – 09:30 | 🎬 系统演示 | 25 min | 技术经理（Dev）+ 产品经理（PO） |
| 09:30 – 09:45 | 验收标准逐项确认 | 15 min | 项目经理（SM）+ 全员 |
| 09:45 – 10:00 | 🚫 禁飞区代码检查 | 15 min | 技术经理（Dev） |
| 10:00 – 10:15 | 🐛 Bug 注入评测练习 | 15 min | 全员 |
| 10:15 – 10:25 | Sprint 回顾 & 改进 | 10 min | 全员 |
| 10:25 – 10:30 | Sprint 2 规划确认 | 5 min | 项目经理（SM） |

---

## 二、Sprint 1 目标回顾

> **Sprint 1 目标**：脚手架搭建 + 数据库 + 数据管道 + 预测模型 + 大屏骨架
>
> **里程碑 M2 验收标准**：数据 → API → 大屏曲线图全链路贯通，Bug 注入练习通过

### 计划 vs 实际

| 模块 | 计划 | 实际 | 偏差 |
|:-----|:-----|:-----|:-----|
| 项目脚手架 | Day 4 | Day 4 ✅ | 准时 |
| 数据库 + Flyway | Day 4 | Day 4 ✅ | 准时 |
| 模拟数据生成 | Day 4 | Day 4 ✅ | 准时 |
| 数据查询 API | Day 5 | Day 5 ✅ | 准时 |
| 数据清洗 + 特征工程 | Day 5 | Day 5 ✅ | 准时 |
| 前端大屏骨架 | Day 5–7 | Day 5–7 ✅ | 正常 |
| Prophet 基线模型 | Day 6 | Day 6 ✅ | 准时 |
| LSTM 模型 | Day 6 | Day 6 ✅ | 准时 |
| 模型对比报告 | Day 6 | Day 6 ✅ | 准时 |
| TorchScript 导出 | Day 6 | Day 6–7 🟡 | 延迟半日 |
| Flask 推理服务 | Day 6 | Day 6–7 🟡 | 联调适配中 |
| 预测对比图 | Day 7 | ⬜ 待验收 | — |
| 统计卡片行 | Day 7 | 🟡 开发中 | — |
| 大屏自适应布局 | Day 7 | ⬜ 待验收 | — |
| Bug 注入评测练习 | Day 7 | ⬜ 本次执行 | — |

---

## 三、🎬 演示脚本（25 min）

### 场景：电力调度员日常监控工作流

> **演示者**：技术经理（Dev）—— 后端 & ML ｜ 产品经理（PO）—— 前端 & 交互

---

#### Act 1 · 数据全链路（6 min）— Dev

| 步骤 | 操作 | 演示内容 | 时间 |
|:-----|:-----|:---------|:----:|
| 1.1 | 启动服务 | 展示 Flask `:5000/health` + Spring Boot `:8080/actuator/health` 均返回 UP | 1 min |
| 1.2 | 数据库验证 | 打开 MySQL，`SELECT COUNT(*) FROM load_data` → ~17,520 条；展示 `prediction_result`、`alert_event`、`conversation` 表结构 | 1 min |
| 1.3 | 数据 API | Swagger UI `http://localhost:8080/api/doc.html` → 调用 `GET /api/v1/data/load?range=7d`，返回 JSON 含 time + load_mw 字段 | 1 min |
| 1.4 | 预测 API | 调用 `POST /api/v1/predict/batch` → 返回 24h 预测值数组；展示 LSTM 7.14 前最后一次预测结果 | 1 min |
| 1.5 | 模型对比 | 打开 [模型对比报告] 展示 LSTM MAPE 3.53% vs Prophet 4.71% 的图表和结论 | 1 min |
| 1.6 | Flask 推理 | `curl -X POST http://localhost:5000/predict/batch` → 返回预测 JSON，验证模型正常加载（非占位假数据） | 1 min |

> ✅ **验收点**：数据从 MySQL → Java API → Swagger → 浏览器全链路可追踪

---

#### Act 2 · 可视化大屏（8 min）— PO

| 步骤 | 操作 | 演示内容 | 时间 |
|:-----|:-----|:---------|:----:|
| 2.1 | 大屏首页 | 打开 `http://localhost:5173` → Dashboard 页面完整渲染（CRT Terminal 暗色主题） | 1 min |
| 2.2 | 历史负荷曲线 | 展示 ECharts 负荷曲线：缩放拖拽（滚轮 + 底部滑块）→ 切换 24h / 7d / 30d 时间范围 → 曲线实时更新 | 2 min |
| 2.3 | 统计卡片 | 顶部 StatCard 行：峰值负荷、谷值负荷、平均负荷、负荷率 四卡片实时数据 | 1 min |
| 2.4 | 预测对比 | 切换到预测视图 → 实际曲线 + 预测曲线叠加显示（不同颜色），曲线首尾相连无断点 | 2 min |
| 2.5 | 实时推送 | 打开浏览器 DevTools → WebSocket 帧 → 展示 `/topic/load` 推送的实时负荷数据 | 1 min |
| 2.6 | 响应式适配 | 调整浏览器窗口 1920→1366→1024，验证布局不崩、图表自适应 | 1 min |

> ✅ **验收点**：调度员可在单一页面完成"查看历史 → 对比预测 → 读取统计"全流程

---

#### Act 3 · 工程资产展示（5 min）— SM

| 步骤 | 操作 | 演示内容 | 时间 |
|:-----|:-----|:---------|:----:|
| 3.1 | 文档体系 | 展示 docs/ 目录：00-13 共 14 份文档完整编号，打开 09-Sprint1技术笔记 展示 27 个踩坑记录结构 | 1 min |
| 3.2 | AI 审计 | 展示 [08-AI协作审计报告](../docs/08-AI协作审计报告.md) 核心结论：94% 合规率、禁飞区零违规、2 个 AI Bug 已记录 | 1 min |
| 3.3 | 项目记忆 | 展示 .agent/ 三文件：architecture.md (15 ADR)、conventions.md (工程规范)、decisions.md (18 决策) | 1 min |
| 3.4 | 一键环境 | 展示 `ml/setup.sh` 76 行 → 新成员 10 分钟跑通全链路（5 步：venv→数据→导入→特征→训练） | 1 min |
| 3.5 | Git 工作流 | 展示 GitHub PR 列表 → 每个 PR 走 feature 分支 → AI Review → 人工确认 → Squash & Merge 流程 | 1 min |

> ✅ **验收点**：工程资产完整——新成员 pull 后看 docs/ + .agent/ + setup.sh 即可上手

---

#### Act 4 · 自由探索（6 min）— 全员

| 步骤 | 操作 | 演示内容 | 时间 |
|:-----|:-----|:---------|:----:|
| 4.1 | 边界测试 | 切换到数据库为空的时间段 → 验证空数据提示而非白屏 | 2 min |
| 4.2 | 告警预览 | 展示 AlertCenter 页面（Sprint 2 预告）→ 当前占位状态 | 1 min |
| 4.3 | Agent 预览 | 展示 AgentChat 页面（Sprint 2 预告）→ 当前占位状态 | 1 min |
| 4.4 | Q&A | 评审人员自由提问、操作、验证 | 2 min |

---

## 四、验收标准逐项确认

### M2 里程碑验收清单

| 编号 | 验收项 | 标准 | 状态 | 证据 |
|:-----|:-----|:-----|:----:|:-----|
| AC-1 | 后端脚手架可运行 | `mvn spring-boot:run` 启动成功，6 张表自动建表 | ⬜ | `:8080/actuator/health` → UP |
| AC-2 | 前端骨架可运行 | `npm run dev` 启动成功，路由跳转正常 | ⬜ | `:5173` 可访问 4 个页面 |
| AC-3 | 数据查询 API 可用 | `GET /api/v1/data/load` 返回有效 JSON | ⬜ | Swagger 调用截图 |
| AC-4 | 模拟数据入库 | `load_data` 表 ≥ 15,000 条 | ⬜ | `SELECT COUNT(*)` |
| AC-5 | Prophet 模型训练完成 | MAPE < 8% | ⬜ | 模型对比报告 |
| AC-6 | LSTM 模型训练完成 | MAPE < 5% | ⬜ | 模型对比报告 |
| AC-7 | Flask 推理服务可用 | `POST /predict/batch` 返回 24 个有效预测值 | ⬜ | curl/Postman 截图 |
| AC-8 | 大屏负荷曲线 | ECharts 曲线可缩放拖拽，切换时间范围 | ⬜ | 演示录屏 |
| AC-9 | 预测对比图 | 实际 + 预测双曲线叠加，曲线首尾相连 | ⬜ | 演示录屏 |
| AC-10 | 统计卡片 | 峰值/谷值/均值/负荷率四卡片正确显示 | ⬜ | 演示录屏 |
| AC-11 | 大屏响应式布局 | 1920/1366/1024 三种分辨率不崩 | ⬜ | 演示录屏 |
| AC-12 | 文档体系完整 | docs/ 00-13 编号完整，5 项人工产出 4/5 就绪 | ⬜ | 目录截图 |
| AC-13 | AI 审计通过 | 禁飞区零违规，AI Bug 有追踪 | ⬜ | 审计报告章节 |
| AC-14 | 🐛 Bug 注入练习 | 3 个注入 Bug 在 30 分钟内定位 + 修复 | ⬜ | 修复 commit 记录 |

### P0 功能覆盖率

| 模块 | 功能点 | 已交付 | 待交付 | 覆盖率 |
|:-----|:------:|:------:|:------:|:------:|
| 数据管道 | 4 | 4 | 0 | 100% |
| 预测模型 | 5 | 5 | 0 | 100% |
| Flask 推理 | 2 | 1 | 1 | 50% |
| 可视化大屏 | 5 | 3 | 2 | 60% |
| 文档 & 工程 | 4 | 4 | 0 | 100% |
| **合计** | **20** | **17** | **3** | **85%** |

---

## 五、🚫 禁飞区代码检查（15 min）

> **规则**：AI 禁飞区代码必须为手写，技术经理逐行讲解核心逻辑。

### NFZ 检查清单

| 编号 | 模块 | 文件 | 行数 | 手写确认 | 讲解要点 |
|:-----|:-----|:-----|:----:|:--------:|:---------|
| NFZ-1 | Agent 核心循环 | `AgentCore.java` | ~200 | ⬜ | Function Calling 编排流程：消息构建 → LLM 调用 → function_call 解析 → ToolRegistry 分发 → 结果回传 |
| NFZ-2 | Prompt 工程 | `buildSystemPrompt()` + Tool Definitions | ~80 | ⬜ | System Prompt 设计思路：角色定位 → 能力边界 → 输出格式约束；Tool Schema JSON 定义规范 |
| NFZ-3 | 告警检测引擎 | `ThresholdDetector.java` + `AlertTemplate.java` | ~150 | ⬜ | 三级告警分级逻辑（红/黄/绿）；固定模板生成策略（`String.format`） |

### NFZ 检查流程

1. **NFZ-3 告警引擎**（5 min）— 代码已交付，优先检查
   - 逐行讲解 `ThresholdDetector.check(currentLoad, threshold)` 分级逻辑
   - 展示 `AlertTemplate.generate()` 的 3 种模板字符串
   - 确认不使用 LLM 生成告警文案（延迟 + 成本理由）

2. **NFZ-1 AgentCore**（5 min）— 代码已交付，优先检查
   - 逐行讲解 Agent 主循环：接收消息 → 构建请求 → 调用 LLM → 解析 function_call → 执行工具 → 回传结果
   - 展示 ToolRegistry 的 Spring Bean 扫描机制

3. **NFZ-2 Prompt**（5 min）— 代码已交付，优先检查
   - 讲解 System Prompt 设计：角色 "电力负荷分析助手" + 能力声明 + 输出格式要求
   - 展示 2 个 Tool Definition（QueryLoadTool / GetStatsTool）的 JSON Schema

> ⚠️ **NFZ 红线**：任一模块无法逐行讲解 → 该模块分数清零。技术经理需提前准备讲解提纲。

---

## 六、🐛 Bug 注入评测练习（15 min）

> **规则**：评审者向代码库中注入 3 个 Bug，团队在 30 分钟内定位 + 修复。本次为 **练习轮**（非正式评测），旨在熟悉 Bug 注入流程。

### Sprint 1 已知 Bug 回顾（复习）

| 编号 | 文件 | 问题 | 严重度 | 修复状态 |
|:-----|:-----|:-----|:------:|:--------:|
| B4 | `DataCleanServiceImpl.java:140` | SLF4J `{:.1f}` Python 格式符 | 🟡 | ⬜ 待修复 |
| B5 | `ml/app.py:134` | 模型未加载时返回假数据 HTTP 200 | 🔴 | ⬜ 待修复 |
| B10 | `AlertEvent.java` | Javadoc 误标 "NFZ-3 AlertTemplate" | 🟢 | ⬜ 待修复 |

### 练习 Bug 注入（模拟）

| Bug 编号 | 注入位置 | Bug 类型 | 预期症状 | 难度 |
|:---------|:---------|:---------|:---------|:----:|
| 练习-1 | `LoadDataController.java` | 查询参数错误 | 时间范围筛选失效，返回全量数据 | ⭐ |
| 练习-2 | `Dashboard/index.tsx` | 图表配置错误 | 预测曲线颜色与图例不一致 | ⭐⭐ |
| 练习-3 | `PredictServiceImpl.java` | 逻辑错误 | 预测结果缓存在 key 过期后未刷新 | ⭐⭐⭐ |

### 练习流程

1. **注入**（2 min）：评审者修改代码，引入 3 个 Bug
2. **发现**（8 min）：团队通过运行系统、查看日志、对比预期行为定位 Bug
3. **修复**（4 min）：每个 Bug 修复 + 验证
4. **回顾**（1 min）：总结 Bug 类型和发现技巧

---

## 七、Sprint 回顾 & 改进

### 做得好 👍

1. **设计先行落地**：D1-D3/D5 四份设计文档在编码前完成，AI 生成代码 100% 符合接口规范
2. **约束式生成占主导**：67% 文件走约束式，返工率最低；Vibe Coding 仅 6%
3. **setup.sh 高 ROI**：76 行 Shell 将环境初始化从 2-3h 压到 10min
4. **AI 审计前置**：Day 4 即完成审计，而非等到 Sprint 评审日
5. **Git 工作流落地**：feature 分支 → PR → AI Review → Squash & Merge 全流程跑通

### 待改进 🔧

1. **跨文件代码重复**：`quickToRange()` 等工具函数需抽取为共享模块
2. **Commit 信息质量**：fix commit 需包含 现象→根因→修复 三段式
3. **ML 模型导出规范**：TorchScript + 元数据 + Scaler + 特征列 四件套缺一不可
4. **前端大组件拆分**：317 行 Dashboard 应拆为独立子组件
5. **Bug 修复滞后**：3 个已知 Bug 待 Sprint 2 修复（B4/B5/B10）
6. **成员贡献不均衡**：提交记录集中在 PM + Dev，PO 和 QA 需提升代码贡献

### Sprint 2 预防措施确认

| 编号 | 措施 | 负责人 | Sprint 2 Day |
|:-----|:-----|:-------|:-------------|
| P1 | 禁飞区 NFZ-1/2 手写骨架 Day 9 前完成 | Dev | Day 9 |
| P2 | Agent 交互规范文档先于代码 | Dev | Day 9 上午 |
| P3 | 抽取 `ml/features.py` 共享模块 | Dev | Day 10 |
| P4 | 修复 B4 + B5 | Dev | Day 9 |
| P5 | CI/CD GitHub Actions | QA | Day 11 |
| P6 | `requirements.txt` 用 `==` 固定版本 | Dev | Day 9 |
| P7 | 前端公共工具抽取 | PO | Day 10 |

---

## 八、Sprint 2 规划确认

> **Sprint 2 周期**：Day 9 – Day 11（2026-07-19 ~ 2026-07-21）  
> **Sprint 目标**：告警检测引擎 + 智能 Agent + 全链路贯通

### Day 9：告警检测 + WebSocket

| 任务 | 负责人 | 优先级 |
|:-----|:-------|:------:|
| 🚫 NFZ-3 ThresholdDetector.java | Dev ✍️ | P0 |
| 🚫 NFZ-3 AlertTemplate.java | Dev ✍️ | P0 |
| WebSocket `/ws/dashboard` STOMP | Dev | P0 |
| 告警事件列表 + 时间线 | PO | P0 |
| 天气信息卡片 + 历史回溯 | PO | P1 |

### Day 10：智能 Agent

| 任务 | 负责人 | 优先级 |
|:-----|:-------|:------:|
| 🚫 NFZ-1 AgentCore.java | Dev ✍️ | P0 |
| 🚫 NFZ-2 buildSystemPrompt() + Tool Definitions | Dev ✍️ | P0 |
| ToolRegistry 工具注册 | Dev | P0 |
| QueryLoadTool + GetStatsTool | Dev | P0 |
| Agent 对话页面 + SSE 流式 | PO | P0 |

### Day 11：集成 + Sprint 2 评审

| 任务 | 负责人 | 优先级 |
|:-----|:-------|:------:|
| 全链路联调 | 全员 | P0 |
| 边界情况处理 | 全员 | P1 |
| 🐛 Bug 注入评测（正式） | 全员 | P0 |
| Sprint 2 评审 | 全员 | P0 |

### Sprint 2 关键风险

| 风险 | 应对 |
|:-----|:-----|
| LLM API 不稳定 | Agent 降级为规则查询 + 固定回复 |
| 禁飞区手写进度慢 | Day 8 提前准备 NFZ 框架代码 |
| WebSocket + STOMP 调试困难 | 先用 Postman/Simple WebSocket Client 独立测试 |

---

## 九、Sprint 1 数据总结

### 交付物统计

| 类别 | 数量 | 明细 |
|:-----|:----:|:-----|
| Java 文件 | 28 | controller(4) + service(5) + mapper(6) + entity(6) + config(4) + common(2) + dto(1) |
| TypeScript 文件 | 16 | pages(4) + components(5) + services(3) + stores(1) + hooks(1) + types(2) |
| Python 文件 | 5 | app.py + train_lstm.py + train_prophet.py + feature_engineering.py + generate_mock_data.py |
| SQL 迁移 | 1 | V1__init_schema.sql (6 张表) |
| 设计文档 | 8 | 00-07（含 5 项人工产出 4/5） |
| 日报 | 5 | 7.12 – 7.16 |
| 经验文档 | 3 | 08-AI审计 / 09-技术笔记 / 10-评审议程 |
| 模型文件 | 5 | lstm_scripted.pt + lstm_meta.pt + scaler × 2 + feature_cols.pkl |
| Shell 脚本 | 1 | ml/setup.sh |

### 团队贡献

| 成员 | 角色 | 代码提交 | 文档产出 | 核心贡献 |
|:-----|:-----|:--------:|:--------:|:---------|
| 黄颢 | SM | 高 | 日报×5、技术笔记、审计报告、.agent/ | Sprint 管理 + 经验沉淀 |
| 杨周禅 | PO | 高 | — | 前端全栈（Dashboard + DataQuery + 预测页 + StatCard） |
| 黄振兴 | Dev | 高 | 模型对比报告 | ML 全栈（Prophet + LSTM + TorchScript + Flask） |
| 李佳骏 | QA | 中 | 测试用例文档骨架 | Flask 接口测试 + MockMvc 集成测试 |

### Bug 统计

| 严重度 | 发现 | 已修复 | 待修复 |
|:------:|:----:|:------:|:------:|
| 🔴 严重 | 3 | 1 | 2 |
| 🟡 中等 | 6 | 5 | 1 |
| 🟢 轻微 | 1 | 0 | 1 |
| **合计** | **10** | **6** | **4** |

---

## 附录 A：演示环境准备清单

| 项目 | 要求 | 负责人 |
|:-----|:-----|:-------|
| MySQL 8.0 | `localhost:3306`，`power_load` 库有数据 | Dev |
| Flask 推理 | `:5000/health` 可访问，模型已加载 | Dev |
| Spring Boot | `:8080` 启动成功，Flyway 已执行 | Dev |
| Vite 前端 | `:5173` 可访问，API 代理正常 | PO |
| Swagger | `:8080/api/doc.html` 可打开 | Dev |
| 浏览器 | 无缓存、DevTools 就绪 | PO |
| 录屏工具 | 准备就绪（如需录制演示过程） | SM |

## 附录 B：Sprint 1 评审签字

| 角色 | 姓名 | 评审结论 | 签字 | 日期 |
|:-----|:-----|:---------|:-----|:-----|
| 项目经理（SM） | 黄颢 | | | |
| 产品经理（PO） | 杨周禅 | | | |
| 技术经理（Dev） | 黄振兴 | | | |
| 测试工程师（QA） | 李佳骏 | | | |

---

> **关联文档**：[00-项目开发计划](./00-项目开发计划.md) · [09-Sprint1技术笔记](./09-Sprint1技术笔记.md) · [08-AI协作审计报告](./08-AI协作审计报告.md) · 项目经理日报 [7.16](./项目经理日报-7.16.md)
>
> **版本记录**
>
> | 版本 | 日期 | 变更 |
> |:-----|:-----|:-----|
> | v1.0 | 2026-07-16 | 初版：90 分钟评审议程 + 演示脚本 + 验收标准 + Bug 注入练习 + Sprint 2 规划 |
