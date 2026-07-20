package com.powerload.agent;

import com.powerload.security.SysUserPrincipal;

/**
 * 用户上下文 ThreadLocal — 工具可从当前线程获取 Agent 调用者身份。
 *
 * <p>AgentCore 在 run() 入口设置，run() 出口清除。</p>
 */
public final class UserContextHolder {

    private static final ThreadLocal<SysUserPrincipal> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {}

    public static void set(SysUserPrincipal user) { HOLDER.set(user); }

    public static SysUserPrincipal get() { return HOLDER.get(); }

    public static void clear() { HOLDER.remove(); }

    /** 获取当前用户角色，未认证时返回 null */
    public static String currentRole() {
        SysUserPrincipal p = HOLDER.get();
        return p != null ? p.getRole() : null;
    }

    /** 获取当前用户 ID，未认证时返回 null */
    public static Long currentUserId() {
        SysUserPrincipal p = HOLDER.get();
        return p != null ? p.getUserId() : null;
    }
}
