package com.powerload.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.powerload.controller")
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        return R.fail(400, "参数校验失败：" + e.getMessage());
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleBadCredentials(org.springframework.security.authentication.BadCredentialsException e) {
        return R.fail(401, "用户名或密码错误");
    }

    @ExceptionHandler(org.springframework.security.authentication.DisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleDisabled(org.springframework.security.authentication.DisabledException e) {
        return R.fail(403, "账户已禁用");
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(org.springframework.security.access.AccessDeniedException e) {
        return R.fail(403, "权限不足");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("服务器内部错误", e);
        return R.fail(500, "服务器内部错误");
    }
}
