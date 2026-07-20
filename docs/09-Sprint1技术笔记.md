# 📝 Sprint 1 技术笔记 — 踩坑记录 & 经验沉淀

> **版本**：v1.0 ｜ **日期**：2026-07-14 ｜ **整理人**：项目管理团队  
> **覆盖范围**：Day 1 – Day 7（Sprint 1：脚手架 → 数据管道 → 预测模型 → 可视化大屏）  
> **数据来源**：Git 提交记录 × 日报问题记录 × AI 审计报告 × 团队讨论

---

## 一、环境与工具链（6 坑）

### 坑 1：Windows Python 虚拟环境路径

| 项目 | 内容 |
|:-----|:-----|
| **现象** | `ml/setup.sh` 中写 `source .venv/bin/activate` 在 Windows 上执行失败 |
| **原因** | Windows venv 的 Scripts 目录在 `.venv/Scripts/`，不是 `.venv/bin/`；pip/python 同理 |
| **解决** | 所有路径改为 `.venv/Scripts/python`、`.venv/Scripts/pip` |
| **教训** | ⚡ **Shell 脚本必须考虑平台差异**。跨平台项目建议用 Python 脚本替代 Shell，或提供 `.bat` 备用脚本 |

### 坑 2：Windows 中文编码问题

| 项目 | 内容 |
|:-----|:-----|
| **现象** | Python 脚本输出中文时出现 `UnicodeEncodeError`，`setup.sh` 打印乱码 |
| **原因** | Windows 终端默认 GBK 编码，Python print 中文时编码不匹配 |
| **解决** | 所有 Python 命令前加 `PYTHONIOENCODING=utf-8` 环境变量：`PYTHONIOENCODING=utf-8 .venv/Scripts/python xxx.py` |
| **教训** | ⚡ **Windows 开发 Python 的经典痛点**。建议团队统一用 UTF-8，或在项目根目录加 `.python-encoding` 配置 |

### 坑 3：Maven 依赖配置反复调整

| 项目 | 内容 |
|:-----|:-----|
| **现象** | `pom.xml` 初期经历多次修改：依赖版本冲突、MyBatis-Plus 配置不对、Spring Boot 版本不匹配 |
| **原因** | 初始 pom.xml 为 AI 生成模板，未针对 JDK 17 + Spring Boot 3.3.x 验证 |
| **解决** | 人工逐一验证：Spring Boot 3.3.x ← JDK 17 ← MyBatis-Plus 3.5.7 兼容链确认 |
| **教训** | ⚡ **AI 生成的 pom.xml 不能直接用**。必须逐个依赖确认版本兼容性，建议锁定 `spring-boot-dependencies` BOM |

### 坑 4：Docker MySQL 连接问题

| 项目 | 内容 |
|:-----|:-----|
| **现象** | Docker MySQL 启动后 Spring Boot 连不上，或连接超时 |
| **原因** | ① Docker 容器端口映射未生效 ② `application-dev.yml` 中 host 写 `localhost` 但 Docker 网络隔离 ③ 首次启动 MySQL 初始化需要时间 |
| **解决** | 开发阶段改用本地 MySQL（`host.docker.internal` 或直接 localhost:3306）；`docker-compose.yml` 仅用于 Day 14 部署 |
| **教训** | ⚡ **开发环境与部署环境分离**。不要迷信 Docker Compose 的开发体验——本地跑 MySQL/Redis 更快更稳 |

### 坑 5：Flyway 迁移脚本首次执行失败

| 项目 | 内容 |
|:-----|:-----|
| **现象** | `V1__init_schema.sql` 执行时报语法错误 |
| **原因** | 建表语句末尾多余的逗号；`TIMESTAMP` 类型默认值写法不兼容 MySQL 8.0 |
| **解决** | 逐表核验 DDL，统一使用 `datetime default current_timestamp` 写法 |
| **教训** | ⚡ **数据库迁移脚本应先在本地手动执行验证**，不要直接交给 Flyway 自动跑 |

### 坑 6：前后端开发环境端口管理

| 项目 | 内容 |
|:-----|:-----|
| **现象** | 启动多个服务时端口冲突：Spring :8080 / Flask :5000 / Vite :5173 / MySQL :3306 / Redis :6379 |
| **原因** | 5 个服务同时运行，部分机器 8080/5000 端口被占用 |
| **解决** | `setup.sh` 最终版明确打印每个服务的启动命令 + 端口；Windows 用 `netstat -ano | findstr :8080` 查占用 |
| **教训** | ⚡ **端口管理纳入环境初始化文档**。建议在 README 中列出所有服务端口 |

---

## 二、Git 协作（4 坑）

### 坑 7：多人直接向 develop 推送 → 合并冲突频发

| 项目 | 内容 |
|:-----|:-----|
| **现象** | Day 2-3 期间，多人同时向 develop 分支提交代码，频繁出现冲突。最严重的一次 `ml/app.py` 合并冲突导致 156 行代码重复和语法错误 |
| **原因** | 没有严格执行 feature 分支策略；对 Git 工作流不熟悉 |
| **解决** | ① 制定分支规范 `main ← develop ← feature/*` ② develop 分支开启保护，禁止直接 push ③ 强制 PR 流程 ④ 站会后 10 分钟实操演练 |
| **教训** | ⚡ **分支策略必须在 Day 1 就严格落地**，不要等出了问题再补救。保护规则 + PR 强制是最有效的手段 |

### 坑 8：合并冲突后 Entity 字段丢失

| 项目 | 内容 |
|:-----|:-----|
| **现象** | PR #4 合并后，6 个 Entity 类的字段大量丢失，只剩骨架 |
| **原因** | 两人同时在 feature 分支上修改 Entity，合并时未仔细检查冲突 diff，直接 accept 了"ours"版本 |
| **解决** | 逐类补全字段 + Javadoc，之后要求合并冲突必须双方共同确认，不可单方面选择 |
| **教训** | ⚡ **Entity 类是冲突高发区**。建议一人负责 Entity 层的最终合并，或分文件开发避免冲突 |

### 坑 9：Commit 信息格式不统一

| 项目 | 内容 |
|:-----|:-----|
| **现象** | 早期提交信息混杂："新增约束"、"约束规则更新"、"相关依赖修改"——无法从 commit log 快速了解改动范围 |
| **原因** | 未提前约定 Conventional Commits 格式 |
| **解决** | 统一为 `feat:/fix:/docs:/chore:/refactor:` 前缀，PR 合并时 Squash & Merge 整理提交 |
| **教训** | ⚡ **Conventional Commits 规则 Day 1 就写入 CLAUDE.md**，并作为 Code Review 检查项 |

### 坑 10：`.gitignore` 遗漏 CSV 文件

| 项目 | 内容 |
|:-----|:-----|
| **现象** | 生成的 `mock_load_data.csv`、`featured_load_data.csv`（几十 MB）被意外提交 |
| **原因** | `.gitignore` 初期未覆盖 `*.csv` |
| **解决** | 添加 `*.csv` 到 `.gitignore`，并用 `git rm --cached` 清除已追踪的 CSV |
| **教训** | ⚡ **数据文件必须在生成脚本首次运行前加入 `.gitignore`**，否则会产生难以清理的大文件历史 |

---

## 三、AI 生成代码（5 坑）

### 坑 11：AI 在 SLF4J 日志中使用 Python 格式符

| 项目 | 内容 |
|:-----|:-----|
| **现象** | `DataCleanServiceImpl.java` 日志输出中出现 `{:.1f}` 原样打印，非格式化数字 |
| **原因** | AI 在多语言训练数据中混淆了 Python f-string `{:.1f}` 与 SLF4J `{}` |
| **解决** | 改为：先用 `String.format("%.1f", value)` 再传入 SLF4J `{}` |
| **教训** | ⚡ **AI 跨语言生成代码时，格式化字符串是高频 Bug**。Review 时重点检查日志、SQL、正则表达式等"跨语言通用但有方言差异"的语法 |

### 坑 12：Flask placeholder 返回假数据而非报错

| 项目 | 内容 |
|:-----|:-----|
| **现象** | `ml/app.py` 在模型未加载时，`/predict/forecast` 返回 `[1000.0] * 24` + HTTP 200 |
| **原因** | AI 为"保证接口可用"而生成占位数据，掩盖了模型未加载的真实问题 |
| **解决** | 应返回 HTTP 503 + `{"error": "model not loaded"}`, 前端据此显示"预测服务不可用" |
| **教训** | ⚡ **永远不要让 AI 生成"优雅降级"的数据**。异常情况必须返回错误码，不能伪造正常数据 |

### 坑 13：AI 生成代码的"过度完整"——一次性生成 317 行 Dashboard

| 项目 | 内容 |
|:-----|:-----|
| **现象** | `Dashboard/index.tsx` 一次性生成了 317 行，包含图表配置、状态管理、UI 渲染、CSS——职责混杂，后续需要拆分为 6 个独立优化 commit |
| **原因** | 无前置 UI 原型约束，AI 自由发挥生成了"最完整的 Dashboard" |
| **解决** | 后续 6 个 fix commit 逐步拆分：动态点数、预测范围、now 标记、加载骨架、错误提示、移除不可用功能 |
| **教训** | ⚡ **大组件必须在开发前定义交互规范**（dataZoom 行为、预测叠加方式、状态结构），限制 AI 的"自由发挥"范围 |

### 坑 14：`quickToRange()` 函数在多文件中重复

| 项目 | 内容 |
|:-----|:-----|
| **现象** | `quickToRange()` 函数在 `Dashboard/index.tsx` 和 `DataQuery/index.tsx` 中完全相同 |
| **原因** | AI 批量生成各页面时独立编码，不知道另一个文件已有相同逻辑 |
| **解决** | 抽取到 `utils/time.ts` — 待 Sprint 2 重构 |
| **教训** | ⚡ **AI 批量生成不会自动去重**。Sprint Review 应检查跨文件重复代码 |

### 坑 15：pandas 3.x 兼容性问题

| 项目 | 内容 |
|:-----|:-----|
| **现象** | `feature_engineering.py` 在 pandas 3.x 环境下运行报错 |
| **原因** | AI 生成的代码基于 pandas 2.x API，3.x 中部分函数签名和默认行为发生变化 |
| **解决** | 手动调整 API 调用，确认与 `requirements.txt` 中版本一致 |
| **教训** | ⚡ **`requirements.txt` 中必须固定版本号**（如 `pandas==2.2.0`），不用 `>=`。AI 倾向于用最新 API，但实际环境可能是旧版本 |

---

## 四、前端踩坑（5 坑）

### 坑 16：ECharts 框选/圈选放大功能不兼容

| 项目 | 内容 |
|:-----|:-----|
| **现象** | 用户无法使用鼠标框选放大部分区域（`dataZoom.brushSelect` 无响应） |
| **原因** | 该功能需要 `toolbox.feature.dataZoom` 配合，且与 `dataZoom-inside` 存在交互冲突 |
| **解决** | 移除框选/圈选，保留滚轮缩放 + 滑块缩放 + 导出图片三种确定可用的交互方式 |
| **教训** | ⚡ **ECharts 的 dataZoom 配置非常脆弱**，添加新交互前应在独立页面测试，确认不冲突后再合入 |

### 坑 17：预测曲线与实际曲线间存在断点

| 项目 | 内容 |
|:-----|:-----|
| **现象** | 切换到预测视图时，实际负荷曲线的终点与预测曲线的起点有明显断口 |
| **原因** | 预测数据从第 1 小时开始画，而实际数据最后一条在第 0 小时——两者在 x 轴上缺少共享数据点 |
| **解决** | 预测数据数组第一位插入实际数据的最后一个点：`[lastTime, lastVal]` + 24 条预测 |
| **教训** | ⚡ **时间序列对比图首尾相接是关键 UX 细节**。一端断开会造成"预测不准"的错觉 |

### 坑 18：图表数据量无上限 → 30 天视图卡顿

| 项目 | 内容 |
|:-----|:-----|
| **现象** | 切换到"近 30 天"视图时，图表渲染明显卡顿 |
| **原因** | 30 天数据约 720 个点，未做截断限制，全部交给 ECharts 渲染 |
| **解决** | 按时间范围动态截断：24h → 500 点、7d → 500 点、30d → 1200 点 |
| **教训** | ⚡ **前端图表必须设置数据量上限**，不能假设后端只返回小数据量 |

### 坑 19：占位页面色值与主题不一致

| 项目 | 内容 |
|:-----|:-----|
| **现象** | AlertCenter/AgentChat/Admin 三个占位页面使用 `#e0e6f0` / `#8892a4` 硬编码色值，与 CRT 终端主题色（`#FF2A2A` / `#EAEAEA`）不统一 |
| **原因** | 占位页面在主题 Token 定制前由 AI 生成，之后未更新 |
| **解决** | 占位页面可直接用 Ant Design Typography 组件，或等 Sprint 2/3 实际开发时一并修正 |
| **教训** | ⚡ **占位代码和主题 Token 应同批生成**，避免时间差导致的风格割裂 |

### 坑 20：Zustand Store 类型定义不完整导致 use 时的 TS 报错

| 项目 | 内容 |
|:-----|:-----|
| **现象** | Dashboard 组件中使用 `useDashboardStore()` 时部分字段 TS 类型推断为 `never` |
| **原因** | Store 中 `predictions` / `forecast` 初始化为 `[] as PredictionResult[]` 写法在 strict 模式下有问题 |
| **解决** | 使用 `initialState` 对象显式标注类型 + `create<DashboardState>()` 泛型 |
| **教训** | ⚡ **Zustand + TypeScript strict 模式下，`create` 泛型显式声明比依赖类型推断更可靠** |

---

## 五、后端踩坑（4 坑）

### 坑 21：MyBatis-Plus `LambdaQueryWrapper` 字段名映射错误

| 项目 | 内容 |
|:-----|:-----|
| **现象** | `LoadDataServiceImpl` 中 `LoadData::getTime` 编译通过但运行时报字段找不到 |
| **原因** | Entity 中 Lombok `@Data` 生成的 getter 命名与 MyBatis-Plus 预期的数据库字段名不一致（`load_mw` → `getLoadMw` 正常，但极个别情况下 Lombok 版本差异导致 getter 不存在） |
| **解决** | 确认 Lombok 版本与 MyBatis-Plus 兼容；核心查询改用显式 `eq("time", value)` 作为匿名 lambda 的 fallback |
| **教训** | ⚡ **Lombok + MyBatis-Plus Lambda 表达式是甜蜜的陷阱**。建议关键查询至少跑一次集成测试，不能用单元测试代替 |

### 坑 22：OkHttp 超时配置不当导致 Flask 调用挂起

| 项目 | 内容 |
|:-----|:-----|
| **现象** | Flask 推理服务偶尔响应慢时，Java 端请求线程被无限期阻塞 |
| **原因** | 初始版 `FlaskInferenceService` 未显式配置 `connectTimeout` 和 `readTimeout` |
| **解决** | 设置 `connectTimeout=10s` + `readTimeout=30s`，超时后抛出异常并返回降级响应 |
| **教训** | ⚡ **任何 HTTP 调用必须显式设置超时**，不能依赖库默认值（OkHttp 默认 connect=10s 但 read=0 即无限） |

### 坑 23：`R<T>` 响应体 JDK 序列化兼容

| 项目 | 内容 |
|:-----|:-----|
| **现象** | `R<T>` 在 Jackson 序列化时 `timestamp` 字段有时为 `null` |
| **原因** | Lombok `@Data` 生成 setter，Jackson 反序列化时通过 setter 覆盖了构造器中初始化的 `System.currentTimeMillis()` |
| **解决** | 将 `timestamp` 字段标记为 `@JsonProperty(access = READ_ONLY)` 或直接用 `final long timestamp = System.currentTimeMillis()` |
| **教训** | ⚡ **Lombok @Data + 构造器初始化字段 = Jackson 反序列化风险**。对只读字段加 `@JsonProperty(access = READ_ONLY)` |

### 坑 24：`@DateTimeFormat` 时区问题

| 项目 | 内容 |
|:-----|:-----|
| **现象** | 前端传 UTC 时间字符串，Controller 解析后变成 UTC+8，查询结果偏移 8 小时 |
| **原因** | `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)` 默认使用系统时区（Asia/Shanghai），而前端 dayjs 默认输出 UTC |
| **解决** | 统一前端 `dayjs().toISOString()` 和后端 `LocalDateTime` 解析时区；或前端传时间戳（epoch millis） |
| **教训** | ⚡ **前后端时间传递统一用 epoch 毫秒或明确时区的 ISO 8601**，不要依赖默认时区 |

---

## 六、ML 踩坑（3 坑）

### 坑 25：Flask 特征工程代码与独立脚本重复

| 项目 | 内容 |
|:-----|:-----|
| **现象** | `ml/app.py` 中 `_engineer_features()` 重新实现了 `feature_engineering.py` 的特征逻辑，而非 import 复用 |
| **原因** | AI 在生成 app.py 时作为独立文件生成，未考虑已有的 feature_engineering.py |
| **解决** | 短期：保持两处（推理需要独立运行）；长期：抽取 `ml/features.py` 共享模块 |
| **教训** | ⚡ **Python 项目应把共享逻辑抽取为独立模块**。AI 生成的"每个文件自包含"模式会导致维护噩梦 |

### 坑 26：TorchScript 模型加载时需要配套 meta 文件

| 项目 | 内容 |
|:-----|:-----|
| **现象** | `ml/app.py` 加载 `lstm_scripted.pt` 后推理失败，shape mismatch |
| **原因** | TorchScript 模型只保存了计算图结构，未保存 `seq_length`、`feature_cols`、`scaler` 等元信息 |
| **解决** | 拆分保存：`lstm_scripted.pt`（模型）+ `lstm_meta.pt`（seq_length）+ `lstm_scaler_x.pkl` + `lstm_scaler.pkl` + `lstm_feature_cols.pkl` |
| **教训** | ⚡ **模型导出 = 模型文件 + 元数据 + Scaler + 特征列列表**。缺一不可，缺一个 = 推理不可用 |

### 坑 27：Prophet 模型在节假日预测偏差大

| 项目 | 内容 |
|:-----|:-----|
| **现象** | 国庆/五一期间的 Prophet 预测值与实际值偏差显著（MAPE > 15%） |
| **原因** | `train_prophet.py` 中 `is_holiday` 特征未传入 Prophet 的 `holidays` 参数，而是作为普通 regressor |
| **解决** | 将节假日作为 Prophet `holidays` DataFrame 传入 `add_country_holidays` 或自定义 holiday 表 |
| **教训** | ⚡ **Prophet 的 holidays 参数与 regressor 效果不同**——前者影响趋势分量，后者仅影响回归分量。节假日信息应走 `holidays` 参数 |

---

## 七、方法论沉淀（5 条核心经验）

### 经验 1：设计先行——5 项人工产出必须在编码前完成

在开始写代码之前，团队输出了架构设计、接口设计、数据模型设计、部署方案设计 4 份文档（D1-D3, D5）。这些文档作为"前置约束"，使 AI 生成的代码有明确规范可依——**所有后端 Controller/Service/Entity 都 100% 符合接口设计文档的预期**。

### 经验 2：三层 AI 范式确实有效——但需要人工检查

本次 Sprint 1 中，67% 的文件走约束式生成（前置规范明确），21% 走精确式（人工骨架 + AI 补全），仅 6% 疑似 Vibe Coding。**约束式代码质量最高、需要返工最少**。Vibe Coding 产出（Dashboard 初始版）后续产生了 6 个 fix commit。

### 经验 3：AI 审计报告应在 Sprint 结束前完成

Day 4 完成的 AI 协作审计报告发现了 2 个 Bug（SLF4J 格式符、Flask 假数据）+ 3 处代码重复。如果等到 Sprint 评审日再做审计，这些问题可能已经在系统中潜伏了 3 天。

### 经验 4：bug fix commit 应该写清楚 root cause

Sprint 1 中一些 fix commit 信息过于简略（如"刷新 bug 修复"、"相关依赖修改"），导致后续难以追溯问题根因。好的 fix commit 应该包含：**现象 → 根因 → 修复方式**（如 `fix: 修复 ml/app.py 合并冲突导致的代码重复和语法错误`）。

### 经验 5：`setup.sh` 是团队的"开发环境保单"

`ml/setup.sh` 是 Sprint 1 投入产出比最高的单个文件（76 行 Shell）。它把"新成员从零到跑通全链路"的时间从 **2-3 小时压缩到 10 分钟**。所有实习项目都应该有一个 `setup.sh`。

---

## 八、Sprint 1 Bug 全记录

| 编号 | 日期 | 文件 | 问题描述 | 严重度 | 修复状态 |
|:-----|:-----|:-----|:---------|:------:|:--------:|
| B1 | 7.12 | 6 个 Entity | 合并冲突导致字段丢失 | 🔴 | ✅ 已修复 |
| B2 | 7.13 | `ml/app.py` | 合并冲突导致代码重复 + 语法错误 | 🔴 | ✅ 已修复 |
| B3 | 7.13 | `feature_engineering.py` | pandas 3.x 兼容性 | 🟡 | ✅ 已修复 |
| B4 | 7.13 | `DataCleanServiceImpl.java` | SLF4J `{:.1f}` Python 格式符 | 🟡 | ⬜ 待修复 |
| B5 | 7.13 | `ml/app.py:134` | 模型未加载时返回假数据 | 🔴 | ⬜ 待修复 |
| B6 | 7.13 | `Dashboard/index.tsx` | 预测曲线与实际曲线间断点 | 🟡 | ✅ 已修复 |
| B7 | 7.13 | `Dashboard/index.tsx` | 框选/圈选放大不可用 | 🟡 | ✅ 已移除 |
| B8 | 7.13 | `Dashboard/index.tsx` | 30 天视图数据量过大卡顿 | 🟡 | ✅ 已截断 |
| B9 | 7.13 | `DataQuery/index.tsx` | 数据刷新 Bug | 🟡 | ✅ 已修复 |
| B10 | 7.13 | `AlertEvent.java` | AI 在 Javadoc 中写 "NFZ-3 AlertTemplate" | 🟢 | ⬜ 待修复 |

> 统计：Sprint 1 共发现 **10 个 Bug**（🔴 3 个 / 🟡 6 个 / 🟢 1 个），其中 7 个已修复，3 个待 Sprint 2 修复。

---

## 九、Sprint 2 预防措施

基于 Sprint 1 的经验，Sprint 2 应采取以下预防措施：

| 编号 | 措施 | 针对的坑 |
|:-----|:-----|:---------|
| P1 | 禁飞区代码（NFZ-1/2/3）手写骨架必须在 Day 8 前完成，防止 AI 提前生成 | #13 |
| P2 | Agent 功能开发前先写交互规范文档（对话流程、工具调用、SSE 事件） | #13 #14 |
| P3 | Python 共享逻辑抽取 `ml/features.py`，消除 `app.py` 重复 | #25 |
| P4 | 修复 B4（SLF4J 格式符）和 B5（Flask 假数据） | #11 #12 |
| P5 | 配置 GitHub Actions CI 自动运行 build + test | #7 #8 |
| P6 | 所有 `requirements.txt` 版本号从 `>=` 改为 `==` | #15 |
| P7 | 前端公共工具函数抽取（`quickToRange`、字体栈、色值常量） | #14 #19 |

---

> **版本记录**
>
> | 版本 | 日期 | 变更 |
> |:-----|:-----|:-----|
> | v1.0 | 2026-07-14 | 初版：27 个踩坑记录 + 5 条核心经验 + 10 个 Bug 全记录 |
>
> **关联文档**：[00-项目开发计划](./00-项目开发计划.md) · [08-AI协作审计报告](./08-AI协作审计报告.md) · 项目经理日报 [7.12](./项目经理日报-7.12.md) / [7.13](./项目经理日报-7.13.md) / [7.14](./项目经理日报-7.14.md)

> **当前状态说明（2026-07-20）**：本文记录 Sprint 1 历史问题，不回写当日结论。当前代码、接口边界、模型策略和后续待办以 [当前实现同步说明](./14-当前实现同步说明.md) 为准。
