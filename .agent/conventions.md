# 工程规范

> 项目记忆 · 团队协作基线 · AI 协作约束

---

## 分支策略

- `main` — 生产分支，仅通过 PR 合并
- `develop` — 开发主线
- `feature/*` — 功能分支（如 `feature/agent-core`、`feature/dashboard`）

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

## PR 规则

- 所有代码合并必须通过 PR
- 至少 1 人 Code Review 后方可合并
- 🚫 禁飞区代码必须标注 `✍️ 手写` 注释，Review 时重点检查

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
| D4 | 测试用例设计 | （Day 9-11 编写） | QA | ✅ |
| D5 | 部署方案设计 | [06-部署方案设计.md](../docs/06-部署方案设计.md) | QA | ✅ |

> 其余设计文档：[01-需求规格说明书](../docs/01-需求规格说明书.md)、[03-技术选型方案](../docs/03-技术选型方案.md)、[00-项目开发计划](../docs/00-项目开发计划.md) — 与 5 项人工产出共同构成完整设计基线。

## 代码风格

### Java
- 单模块 Maven 项目，包结构按 `controller → service → mapper → entity` 分层
- 统一响应 `R<T>`，统一异常处理 `GlobalExceptionHandler`
- MyBatis-Plus Lambda QueryWrapper 优先（避免字段名魔法字符串）

### TypeScript / React
- 组件按功能拆分，页面与通用组件分离
- 状态管理使用 Zustand，按领域拆分 Store
- ECharts 使用 `echarts-for-react` 封装

### 通用
- 所有注释、文档使用中文
- 代码命名使用英文（camelCase / PascalCase）
