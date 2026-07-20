# 工程规范

> 项目记忆 · 团队协作基线 · AI 协作约束

---

## 分支策略

- `main` — 生产分支，仅通过 PR 合并
- `develop` — 开发主线
- `feature/*` — 功能分支（如 `feature/agent-core`、`feature/dashboard`）
- Codex 辅助开发分支使用 `codex/` 前缀，例如 `codex/p2-quality-forecast-review`

## Commit 规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/)：

| 类型 | 用途 |
|:-----|:-----|
| `feat:` | 新功能 |
| `fix:` | Bug 修复 |
| `docs:` | 文档变更 |
| `refactor:` | 代码重构（不改变功能） |
| `test:` | 测试相关 |
| `chore:` | 构建/工具/CI 变更 |

项目当前中文优先：界面文本、注释、文档和提交说明使用中文；`LSTM`、`Prophet`、
`TorchScript`、接口路径和字段名等技术标识保留原文。

## PR 规则

- 所有代码合并必须通过 PR
- 至少 1 人 Code Review 后方可合并
- 🚫 禁飞区代码必须标注 `✍️ 手写` 注释，Review 时重点检查
- AI 审查 + 人工确认流程详见 [07-AI代码审查SOP.md](../docs/07-AI代码审查SOP.md)

### PR 工作流（Sprint 1 落地版）

```
develop → feature/* → git push → GitHub PR
                                → AI 审查 (Claude Code /code-review)
                                → 人工 Reviewer 确认
                                → Squash & Merge → develop
                                → 删除 feature 分支
```

## 三层 AI 协作范式

| 层级 | 模式 | 规则 |
|:-----|:-----|:-----|
| 💡 启发式 | 讨论方案 | AI 辅助拓展思路，最终决策由人做出 |
| ✍️ 精确式 | 编写代码 | 人手写基础代码，AI 辅助优化迭代 |
| 🔒 约束式 | 可控输出 | 前置定义接口规范/编码约束，限制 AI 输出范围 |

> 🚫 **硬性红线**：禁止无约束 Vibe Coding。所有 AI 生成代码必须经过 Code Review。

## AI 禁飞区

| 编号 | 模块 | 文件 | 规则 |
|:-----|:-----|:-----|:-----|
| NFZ-1 | Agent 核心循环 | `AgentCore.java` | ✍️ 手写，AI 只看不写 |
| NFZ-2 | Prompt 工程 | `buildSystemPrompt()` + Tool Definitions | ✍️ 手写，AI 只看不写 |
| NFZ-3 | 告警检测引擎 | `ThresholdDetector.java` + `AlertTemplate.java` | ✍️ 手写，AI 只看不写 |

**禁飞区代码注释规范**：

```java
// 🚫 NFZ-1 禁飞区 · 手写代码 · 禁止 AI 生成
// 职责：Agent Function Calling 核心编排
// 依赖：ToolRegistry, OkHttpClient
public class AgentCore { ... }
```

## 设计先行底线（5 项人工产出）

| 编号 | 文档 | 对应文件 | 负责人 | 禁止 AI 全量生成 |
|:-----|:-----|:---------|:-------|:----------------:|
| D1 | 架构设计文档 | [02-系统架构设计.md](../docs/02-系统架构设计.md) | 技术经理 | ✅ |
| D2 | 接口设计文档 | [04-接口设计文档.md](../docs/04-接口设计文档.md) | 技术经理 | ✅ |
| D3 | 数据模型设计 | [05-数据模型设计.md](../docs/05-数据模型设计.md) | 技术经理 | ✅ |
| D4 | 测试用例设计 | [13-Sprint1测试用例文档.md](../docs/13-Sprint1测试用例文档.md) | QA | ✅ |
| D5 | 部署方案设计 | [06-部署方案设计.md](../docs/06-部署方案设计.md) | QA | ✅ |

> 其余设计文档：[01-需求规格说明书](../docs/01-需求规格说明书.md)、[03-技术选型方案](../docs/03-技术选型方案.md)、[00-项目开发计划](../docs/00-项目开发计划.md) — 与 5 项人工产出共同构成完整设计基线。

## 代码风格

### Java
- 包名：`com.powerload`
- 单模块 Maven 项目，包结构按 `controller → service → mapper → entity` 分层
- 统一响应 `R<T>`，统一异常处理 `GlobalExceptionHandler`（`basePackages = "com.powerload.controller"`）
- MyBatis-Plus Lambda QueryWrapper 优先（避免字段名魔法字符串）

### TypeScript / React
- 组件按功能拆分，页面与通用组件分离
- 状态管理使用 Zustand，按领域拆分 Store
- ECharts 使用 `echarts-for-react` 封装

### 通用
- 所有注释、文档使用中文
- 代码命名使用英文（camelCase / PascalCase）

---

## 开发环境速查

| 项目 | 值 |
|:-----|:---|
| 数据库 | MySQL 8.0 `localhost:3306` / 库 `power_load` / 密码 `123456` |
| 后端端口 | `8080` |
| 前端端口 | `5173`（Vite 代理 `/api` → `:8080`） |
| ML 推理 | `5000`（Python Flask） |
| Redis | **未安装**，dev profile 已排除自动配置 |
| Docker | **未安装**，本地直连 MySQL |
| Maven | `backend/apache-maven-3.9.16/` → `./apache-maven-3.9.16/bin/mvn` |

### 前端启动（PowerShell）

```powershell
cd frontend
& "C:\Program Files\nodejs\npm.cmd" run dev
```

### 后端启动

VS Code 中打开 `PowerLoadApplication.java` → 点击 `Run`。

### Flyway 迁移

应用启动时自动执行。V1 已创建 6 张表：
`load_data` / `prediction_result` / `alert_event` / `alert_rule` / `model_version` / `conversation`

### 新成员环境初始化

```bash
cd ml && bash setup.sh   # 一键：venv → 模拟数据 → MySQL导入 → 特征工程 → 模型训练
```

### docs/ 编号方案

| 编号 | 文档 |
|:-----|:-----|
| 00 | 项目开发计划 |
| 01-06 | 设计文档（需求/架构/技术选型/接口/数据模型/部署） |
| 07 | AI 代码审查 SOP |
| 08 | AI 协作审计报告 |
| 09 | Sprint 1 技术笔记 |
| 10-12 | Day 4-6 测试报告 |
| 13 | Sprint 1 测试用例文档 |
| 14 | 当前实现同步说明 |
| 日报 | 项目经理日报，保留当日事实，不回写历史结论 |

所有设计文档、测试报告、日报和项目记忆文档都应在结尾或适当位置链接
[当前实现同步说明](../docs/14-当前实现同步说明.md)。历史文档保留原始背景，
与当前实现不一致时必须明确标注“历史口径”。

### 常见坑

| 问题 | 原因 | 解决 |
|:-----|:-----|:-----|
| `Unsupported character encoding 'utf8mb4'` | JDBC 不认 MySQL 内部字符集名 | 连接串用 `characterEncoding=UTF-8` |
| `/actuator/health` 404 | pom.xml 缺 `spring-boot-starter-actuator` | 已补充依赖 |
| `GlobalExceptionHandler` 拦截 Actuator | `@RestControllerAdvice` 无范围限制 | 加 `basePackages = "com.powerload.controller"` |
| 前端 `npm` 不识别 | PowerShell 未加载 Node.js PATH | 用完整路径 `"C:\Program Files\nodejs\npm.cmd"` |
| Windows Python venv 路径错误 | `.venv/bin/` 不存在于 Windows | 使用 `.venv/Scripts/python` |
| Python 中文输出乱码 | Windows 终端默认 GBK | 命令前加 `PYTHONIOENCODING=utf-8` |
| AI 生成 Java 代码混入 Python 格式符 | AI 跨语言混淆 | Review 重点检查日志/SQL/正则 |
| Flask 模型未加载时返回假数据 | AI 生成"优雅降级"占位值 | 异常必须返回 HTTP 503 |
| ECharts dataZoom 框选与 inside 冲突 | ECharts 内部交互模式互斥 | 新交互功能先在独立页面测试 |
| pandas 3.x API 不兼容 | AI 基于 pandas 2.x 生成 | `requirements.txt` 版本用 `==` 不用 `>=` |
| Python 特征工程代码重复 | AI 逐文件独立生成不复用 | 抽取 `ml/features.py` 共享模块 |
