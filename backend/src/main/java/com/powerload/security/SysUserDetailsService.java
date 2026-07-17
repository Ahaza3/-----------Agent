package com.powerload.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.entity.SysUser;
import com.powerload.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class SysUserDetailsService implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var wrapper = new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username);
        SysUser user = sysUserMapper.selectOne(wrapper);
        if (user == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        if (user.getIsActive() != null && user.getIsActive() == 0) {
            throw new DisabledException("账户已禁用");
        }
        return new User(
                String.valueOf(user.getId()),
                user.getPasswordHash(),
                Collections.singletonList(() -> "ROLE_" + user.getRole()));
    }
}
