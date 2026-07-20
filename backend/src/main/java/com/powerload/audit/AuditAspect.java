package com.powerload.audit;

import com.powerload.entity.OperationLog;
import com.powerload.mapper.OperationLogMapper;
import com.powerload.security.SysUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final OperationLogMapper operationLogMapper;
    private final HttpServletRequest request;

    @Around("@annotation(audit)")
    public Object logOperation(ProceedingJoinPoint jp, AuditLog audit) throws Throwable {
        long start = System.currentTimeMillis();
        String result = "SUCCESS";
        String detail = "";
        try {
            return jp.proceed();
        } catch (Throwable e) {
            result = "FAILURE";
            detail = sanitize(e.getMessage(), 200);
            throw e;
        } finally {
            try {
                long duration = System.currentTimeMillis() - start;
                OperationLog op = new OperationLog();
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof SysUserPrincipal principal) {
                    op.setUserId(principal.getUserId());
                    op.setUsername(principal.getUsername());
                    op.setRole(principal.getRole());
                }
                op.setModule(audit.module());
                op.setAction(audit.action());
                op.setRequestMethod(request.getMethod());
                op.setRequestPath(request.getRequestURI());
                op.setResult(result);
                op.setIpAddress(request.getRemoteAddr());
                op.setDurationMs(duration);
                op.setDetail(detail);
                op.setCreatedAt(LocalDateTime.now());
                operationLogMapper.insert(op);
            } catch (Exception ex) {
                log.error("审计日志写入失败", ex);
            }
        }
    }

    private static String sanitize(String s, int max) {
        if (s == null) return "";
        String clean = s.replaceAll("[\r\n\t]", " ");
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }
}
