package com.powerload.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerload.audit.AuditLog;
import com.powerload.common.R;
import com.powerload.dto.response.UserInfo;
import com.powerload.entity.OperationLog;
import com.powerload.entity.SysUser;
import com.powerload.mapper.OperationLogMapper;
import com.powerload.security.SysUserPrincipal;
import com.powerload.service.AuthService;
import com.powerload.service.SystemHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final AuthService authService;
    private final SystemHealthService healthService;
    private final OperationLogMapper operationLogMapper;

    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        return R.ok(healthService.check());
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public R<List<UserInfo>> listUsers() {
        return R.ok(authService.listUserInfos());
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @AuditLog(module = "用户管理", action = "创建用户")
    public R<UserInfo> createUser(@RequestBody Map<String, String> body) {
        SysUser user = new SysUser();
        user.setUsername(body.get("username"));
        user.setDisplayName(body.getOrDefault("displayName", body.get("username")));
        user.setRole(body.getOrDefault("role", "DISPATCHER"));
        user.setEmail(body.getOrDefault("email", ""));
        SysUser created = authService.create(user, body.get("password"));
        return R.ok(toInfo(created));
    }

    @PutMapping("/users/{id}/role")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @AuditLog(module = "用户管理", action = "修改角色")
    public R<Void> updateRole(@PathVariable Long id, @RequestBody Map<String, String> body,
                               @AuthenticationPrincipal SysUserPrincipal principal) {
        if (principal.getUserId().equals(id)) throw new IllegalArgumentException("不能修改自己的角色");
        authService.updateRole(id, body.get("role"));
        return R.ok();
    }

    @PutMapping("/users/{id}/status")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @AuditLog(module = "用户管理", action = "修改状态")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                 @AuthenticationPrincipal SysUserPrincipal principal) {
        if (principal.getUserId().equals(id)) throw new IllegalArgumentException("不能禁用自己");
        boolean active = (boolean) body.getOrDefault("active", true);
        if (!active && "SYSTEM_ADMIN".equals(authService.getById(id).getRole())) {
            long count = authService.countActiveByRole("SYSTEM_ADMIN");
            if (count <= 1) throw new IllegalArgumentException("不能禁用最后一个启用的 SYSTEM_ADMIN");
        }
        authService.updateStatus(id, active);
        return R.ok();
    }

    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @AuditLog(module = "用户管理", action = "重置密码")
    public R<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        authService.resetPassword(id, body.get("password"));
        return R.ok();
    }

    @GetMapping("/logs")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public R<Page<OperationLog>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String result) {
        var wrapper = new LambdaQueryWrapper<OperationLog>()
                .eq(module != null, OperationLog::getModule, module)
                .eq(result != null, OperationLog::getResult, result)
                .orderByDesc(OperationLog::getCreatedAt);
        return R.ok(operationLogMapper.selectPage(new Page<>(page, size), wrapper));
    }

    private UserInfo toInfo(SysUser u) {
        return new UserInfo(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.getEmail());
    }
}
