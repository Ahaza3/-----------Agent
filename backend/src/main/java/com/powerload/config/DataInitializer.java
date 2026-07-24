package com.powerload.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.entity.GridNode;
import com.powerload.entity.SysUser;
import com.powerload.mapper.GridNodeMapper;
import com.powerload.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 开发环境默认数据初始化。
 *
 * <p>初始化逻辑按用户名幂等执行，确保已有数据库也能补齐新增的变电站运维人员。</p>
 */
@Slf4j
@Component
@Profile({"dev", "docker"})
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final GridNodeMapper gridNodeMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        ensureUser("admin", "admin123", "系统管理员", "SYSTEM_ADMIN");
        ensureUser("dispatcher", "disp123", "调度员张三", "DISPATCHER");
        ensureUser("operator", "oper123", "运维李四", "OPERATOR");
        SysUser east = ensureUser("operator-east", "oper-east123", "东部运维王五", "OPERATOR");
        SysUser west = ensureUser("operator-west", "oper-west123", "西部运维赵六", "OPERATOR");

        bindSubstation("SUBSTATION-EAST", east.getId());
        bindSubstation("SUBSTATION-WEST", west.getId());
        log.info("默认用户与变电站责任域初始化完成");
    }

    private SysUser ensureUser(String username, String password, String displayName, String role) {
        SysUser existing = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (existing != null) {
            return existing;
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setEmail(username + "@demo.local");
        user.setIsActive(1);
        user.setCreatedAt(LocalDateTime.now());
        sysUserMapper.insert(user);
        return user;
    }

    private void bindSubstation(String nodeCode, Long userId) {
        if (userId == null) {
            return;
        }
        GridNode node = gridNodeMapper.selectOne(new LambdaQueryWrapper<GridNode>()
                .eq(GridNode::getNodeCode, nodeCode));
        if (node == null || userId.equals(node.getResponsibleUserId())) {
            return;
        }

        GridNode update = new GridNode();
        update.setId(node.getId());
        update.setResponsibleUserId(userId);
        gridNodeMapper.updateById(update);
    }
}
