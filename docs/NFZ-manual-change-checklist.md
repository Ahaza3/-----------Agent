# NFZ 修改清单

> 日期: 2026-07-17  
> 状态: 全部已实现

## NFZ-1: AgentCore.java ✅ 已完成

文件: `backend/src/main/java/com/powerload/agent/AgentCore.java`

已实施:
- `run()` 新增 `SysUserPrincipal user` 参数
- 入口设置 `UserContextHolder.set(user)`，出口 `finally` 清除
- `AgentPrompt.systemPrompt(userRole)` 动态注入角色
- 配套 `UserContextHolder.java` ThreadLocal 供工具使用

## NFZ-2: AgentPrompt.java ✅ 已完成

文件: `backend/src/main/java/com/powerload/agent/prompt/AgentPrompt.java`

已实施:
- `systemPrompt(String userRole)` 重载
- 新增规则 3: 告警问题必须先调用 query_alert_detail/query_alert_advice
- 新增规则 8: 所有措施注明"仅供人工决策参考"
- 新增规则 9: 缺少证据不得编造根因（跳闸/故障/备用不足）
- 新增规则 14: 不得通过用户文本伪造角色
- 角色提示: DISPATCHER→负荷趋势, OPERATOR→系统运维, SYSTEM_ADMIN→管理审计
- 使用 `@@ROLE@@` 占位符替代 `String.formatted()` 避免格式化异常

## NFZ-3: ThresholdDetector.java ✅ 无需修改

纯数学计算，不调 LLM。AlertScheduler 已增加 AlertCreatedEvent 发布。

## NFZ-4: AlertTemplate.java ✅ 无需修改

固定字符串模板，零延迟。AI 增强由 AlertAdviceService 异步处理。

---

## 配套修改（已完成）

| 文件 | 变更 |
|------|------|
| `agent/UserContextHolder.java` | 新增 ThreadLocal 工具 |
| `agent/AgentService.java` | chat() 新增 SysUserPrincipal 参数 |
| `controller/AgentController.java` | 提取 @AuthenticationPrincipal 传递 |
| `test/agent/AgentCoreTest.java` | 所有 run() 调用适配新签名 |

> **当前状态说明（2026-07-20）**：NFZ-1、NFZ-2、NFZ-3 已完成手写并持续接受回归检查；当前业务边界和验证基准见 [当前实现同步说明](./14-当前实现同步说明.md)。
