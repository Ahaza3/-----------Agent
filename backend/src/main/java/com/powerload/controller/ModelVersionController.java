package com.powerload.controller;

import com.powerload.audit.AuditLog;
import com.powerload.common.R;
import com.powerload.entity.ModelVersion;
import com.powerload.entity.ModelTrainingTask;
import com.powerload.service.ModelVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 预测模型版本管理接口。
 */
@RestController
@RequestMapping("/api/v1/model")
@RequiredArgsConstructor
public class ModelVersionController {

    private final ModelVersionService modelVersionService;

    @GetMapping("/versions")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SYSTEM_ADMIN')")
    public R<List<ModelVersion>> versions() {
        return R.ok(modelVersionService.listVersions());
    }

    @PostMapping("/versions/sync")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SYSTEM_ADMIN')")
    @AuditLog(module = "模型管理", action = "同步本地模型版本")
    public R<List<ModelVersion>> syncLocalArtifacts() {
        return R.ok(modelVersionService.syncLocalArtifacts());
    }

    /**
     * 激活指定版本，同时作为回滚到历史版本的统一操作。
     */
    @PutMapping("/versions/{id}/activate")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SYSTEM_ADMIN')")
    @AuditLog(module = "模型管理", action = "发布或回滚模型版本")
    public R<Map<String, Object>> activate(@PathVariable Long id,
                                            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return R.ok(modelVersionService.activate(id, requestId));
    }

    @PostMapping("/retrain")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SYSTEM_ADMIN')")
    @AuditLog(module = "模型管理", action = "手动重训练模型")
    public R<Map<String, Object>> retrain(@RequestBody Map<String, String> body) {
        return R.ok(modelVersionService.startRetrain(body.getOrDefault("modelName", "LSTM")));
    }

    @GetMapping("/retrain/status")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SYSTEM_ADMIN')")
    public R<Map<String, Object>> retrainStatus() {
        return R.ok(modelVersionService.retrainStatus());
    }

    @GetMapping("/retrain/history")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SYSTEM_ADMIN')")
    public R<List<ModelTrainingTask>> trainingHistory() {
        return R.ok(modelVersionService.trainingHistory());
    }
}
