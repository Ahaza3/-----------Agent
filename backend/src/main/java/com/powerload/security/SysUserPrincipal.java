package com.powerload.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysUserPrincipal {
    private Long userId;
    private String username;
    private String role;
}
