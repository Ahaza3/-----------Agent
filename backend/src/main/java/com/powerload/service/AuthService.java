package com.powerload.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.dto.response.LoginResponse;
import com.powerload.dto.response.UserInfo;
import com.powerload.entity.SysUser;
import com.powerload.mapper.SysUserMapper;
import com.powerload.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final JwtUtils jwtUtils;

    public LoginResponse login(String username, String password) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (DisabledException e) {
            throw new DisabledException("账户已禁用");
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        var wrapper = new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username);
        SysUser user = sysUserMapper.selectOne(wrapper);

        user.setLastLogin(LocalDateTime.now());
        sysUserMapper.updateById(user);

        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getUsername());

        UserInfo info = new UserInfo(user.getId(), user.getUsername(),
                user.getDisplayName(), user.getRole(), user.getEmail());

        return new LoginResponse(accessToken, refreshToken, "Bearer", 1800, info);
    }

    public LoginResponse refresh(String refreshToken) {
        var claims = jwtUtils.parseToken(refreshToken);
        if (!jwtUtils.isRefreshToken(claims)) {
            throw new BadCredentialsException("无效的 Refresh Token");
        }
        Long userId = jwtUtils.getUserId(claims);
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getIsActive())) {
            throw new DisabledException("账户不可用");
        }
        String newAccess = jwtUtils.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String newRefresh = jwtUtils.generateRefreshToken(user.getId(), user.getUsername());
        UserInfo info = new UserInfo(user.getId(), user.getUsername(),
                user.getDisplayName(), user.getRole(), user.getEmail());
        return new LoginResponse(newAccess, newRefresh, "Bearer", 1800, info);
    }

    public UserInfo me(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        return new UserInfo(user.getId(), user.getUsername(),
                user.getDisplayName(), user.getRole(), user.getEmail());
    }

    public SysUser getById(Long id) {
        return sysUserMapper.selectById(id);
    }

    public List<SysUser> listAll() {
        return sysUserMapper.selectList(null);
    }

    public SysUser create(SysUser user, String rawPassword) {
        var existing = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()));
        if (existing != null) throw new IllegalArgumentException("用户名已存在");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setIsActive(1);
        user.setCreatedAt(LocalDateTime.now());
        sysUserMapper.insert(user);
        return user;
    }

    public void updateStatus(Long id, boolean active) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        user.setIsActive(active ? 1 : 0);
        sysUserMapper.updateById(user);
    }

    public void updateRole(Long id, String role) {
        if (!List.of("DISPATCHER", "OPERATOR", "SYSTEM_ADMIN").contains(role)) {
            throw new IllegalArgumentException("无效角色: " + role);
        }
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        user.setRole(role);
        sysUserMapper.updateById(user);
    }

    public void resetPassword(Long id, String newPassword) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(user);
    }

    public long countActiveByRole(String role) {
        return sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getRole, role)
                        .eq(SysUser::getIsActive, 1)).size();
    }

    public List<UserInfo> listUserInfos() {
        return sysUserMapper.selectList(null).stream().map(u ->
                new UserInfo(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.getEmail())
        ).collect(Collectors.toList());
    }
}
