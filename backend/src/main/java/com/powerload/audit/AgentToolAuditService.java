package com.powerload.audit;

import com.powerload.entity.OperationLog;
import com.powerload.mapper.OperationLogMapper;
import com.powerload.security.SysUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolAuditService {

    private final OperationLogMapper operationLogMapper;

    public void record(SysUserPrincipal user, String toolName, String result, long durationMs, String failureReason) {
        try {
            OperationLog log = new OperationLog();
            if (user != null) {
                log.setUserId(user.getUserId());
                log.setUsername(user.getUsername());
                log.setRole(user.getRole());
            }
            log.setModule("AGENT_TOOL");
            log.setAction(toolName);
            log.setRequestMethod("AGENT_TOOL");
            log.setRequestPath("/api/v1/agent/chat");
            log.setResult(result);
            log.setDurationMs(durationMs);
            log.setDetail(sanitizeFailureReason(failureReason));
            log.setCreatedAt(LocalDateTime.now());
            operationLogMapper.insert(log);
        } catch (Exception e) {
            log.error("Agent 工具审计写入失败: tool={}, result={}", toolName, result, e);
        }
    }

    private String sanitizeFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "";
        }
        return failureReason.replaceAll("[^A-Z0-9_]", "_");
    }
}
