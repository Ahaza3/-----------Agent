package com.powerload.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.entity.SysUser;
import com.powerload.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 开发环境数据初始化 — 创建默认管理员账号。
 *
 * <p>仅 dev profile 生效；生产环境由运维手动创建用户。</p>
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        long count = sysUserMapper.selectCount(null);
        if (count > 0) {
            log.info("已有 {} 个用户，跳过初始化", count);
            return;
        }

        // 默认管理员
        createUser("admin", "admin123", "系统管理员", "SYSTEM_ADMIN");
        // 默认调度员
        createUser("dispatcher", "disp123", "调度员张三", "DISPATCHER");
        // 默认运维
        createUser("operator", "oper123", "运维李四", "OPERATOR");

        log.info("默认用户初始化完成: admin/dispatcher/operator (密码均为用户名+123)");
    }

    private void createUser(String username, String password, String displayName, String role) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setEmail(username + "@demo.local");
        user.setIsActive(1);
        user.setCreatedAt(LocalDateTime.now());
        sysUserMapper.insert(user);
    }
}
