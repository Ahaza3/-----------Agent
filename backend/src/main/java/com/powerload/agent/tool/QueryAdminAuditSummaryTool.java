package com.powerload.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.agent.Tool;
import com.powerload.agent.ToolResult;
import com.powerload.entity.OperationLog;
import com.powerload.entity.SysUser;
import com.powerload.mapper.OperationLogMapper;
import com.powerload.mapper.SysUserMapper;
import com.powerload.service.SystemHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class QueryAdminAuditSummaryTool implements Tool {

    private final SysUserMapper sysUserMapper;
    private final OperationLogMapper operationLogMapper;
    private final SystemHealthService systemHealthService;

    @Override
    public String name() {
        return "query_admin_audit_summary";
    }

    @Override
    public java.util.Set<String> allowedRoles() {
        return java.util.Set.of("SYSTEM_ADMIN");
    }

    @Override
    public String description() {
        return "查询系统管理员审计摘要，包括用户角色分布、停用账号、最近失败操作和系统健康状态。适用于权限审计、操作日志风险和系统管理建议。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        return schema;
    }

    @Override
    public ToolResult execute(String args) {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getRole)
                .orderByAsc(SysUser::getUsername));
        Map<String, Long> roleCounts = users.stream()
                .collect(Collectors.groupingBy(SysUser::getRole, LinkedHashMap::new, Collectors.counting()));
        long disabled = users.stream().filter(user -> user.getIsActive() == null || user.getIsActive() != 1).count();

        List<OperationLog> failures = operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                .eq(OperationLog::getResult, "FAILURE")
                .orderByDesc(OperationLog::getCreatedAt)
                .last("LIMIT 8"));
        List<Map<String, Object>> failureItems = new ArrayList<>();
        for (OperationLog log : failures) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
            item.put("username", log.getUsername());
            item.put("role", log.getRole());
            item.put("module", log.getModule());
            item.put("action", log.getAction());
            item.put("detail", log.getDetail());
            failureItems.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("source", "MOCK_ADMIN_AUDIT");
        data.put("userCount", users.size());
        data.put("roleCounts", roleCounts);
        data.put("disabledUserCount", disabled);
        data.put("recentFailureLogs", failureItems);
        data.put("health", systemHealthService.check());
        data.put("recommendedActions", List.of(
                "复核失败操作是否集中在同一用户或模块",
                "确认停用账号不会影响演示和工单流转",
                "检查 Flask、LLM 和 MySQL 状态是否满足答辩演示"));

        return ToolResult.ok("管理员审计摘要已生成：用户 " + users.size()
                + " 个，最近失败操作 " + failures.size() + " 条。", data);
    }
}
