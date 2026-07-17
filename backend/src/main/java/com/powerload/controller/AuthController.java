package com.powerload.controller;

import com.powerload.common.R;
import com.powerload.dto.request.LoginRequest;
import com.powerload.dto.request.RefreshRequest;
import com.powerload.dto.response.LoginResponse;
import com.powerload.dto.response.UserInfo;
import com.powerload.security.SysUserPrincipal;
import com.powerload.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody LoginRequest req) {
        return R.ok(authService.login(req.getUsername(), req.getPassword()));
    }

    @PostMapping("/refresh")
    public R<LoginResponse> refresh(@RequestBody RefreshRequest req) {
        return R.ok(authService.refresh(req.getRefreshToken()));
    }

    @GetMapping("/me")
    public R<UserInfo> me(@AuthenticationPrincipal SysUserPrincipal principal) {
        return R.ok(authService.me(principal.getUserId()));
    }
}
