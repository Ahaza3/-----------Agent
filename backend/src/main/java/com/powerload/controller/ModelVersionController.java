package com.powerload.controller;

import com.powerload.audit.AuditLog;
import com.powerload.common.R;
import com.powerload.entity.ModelVersion;
import com.powerload.service.ModelVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /**
     * 激活指定版本，同时作为回滚到历史版本的统一操作。
     */
    @PutMapping("/versions/{id}/activate")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SYSTEM_ADMIN')")
    @AuditLog(module = "模型管理", action = "发布或回滚模型版本")
    public R<ModelVersion> activate(@PathVariable Long id) {
        return R.ok(modelVersionService.activate(id));
    }
}
