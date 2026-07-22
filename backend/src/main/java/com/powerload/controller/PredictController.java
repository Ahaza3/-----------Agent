package com.powerload.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerload.audit.AuditLog;
import com.powerload.common.R;
import com.powerload.dto.response.ForecastResponse;
import com.powerload.dto.response.ForecastRunResponse;
import com.powerload.entity.ForecastRun;
import com.powerload.security.SysUserPrincipal;
import com.powerload.service.ForecastRunService;
import com.powerload.service.PredictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 负荷预测接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/predict")
@RequiredArgsConstructor
public class PredictController {

    private final PredictService predictService;
    private final ForecastRunService forecastRunService;

    /**
     * 获取未来 24 小时负荷预测
     *
     * <p>链式调用: DB → 特征工程(Flask) → LSTM 推理 → 返回 24 个负荷值</p>
     *
     * @return 预测响应（24 个预测值 + 模型名称）
     */
    @GetMapping("/forecast")
    public R<ForecastResponse> forecast(
            @RequestParam(required = false) Long nodeId,
            @RequestParam(required = false) String idempotencyKey) {
        ForecastResponse data = predictService.forecast(nodeId, idempotencyKey);
        return R.ok(data);
    }

    @GetMapping("/runs")
    @AuditLog(module = "预测批次", action = "查询预测批次")
    public R<Page<ForecastRunResponse>> runs(
            @RequestParam(required = false) Long nodeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SysUserPrincipal principal) {
        boolean internal = principal != null && ("OPERATOR".equals(principal.getRole())
                || "SYSTEM_ADMIN".equals(principal.getRole()));
        Page<ForecastRun> result = forecastRunService.page(nodeId, internal ? status : "COMPLETED",
                start, end, page, Math.min(Math.max(size, 1), 100));
        Page<ForecastRunResponse> response = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        response.setRecords(result.getRecords().stream().map(run -> toResponse(run, internal)).toList());
        return R.ok(response);
    }

    private ForecastRunResponse toResponse(ForecastRun run, boolean includeFailureReason) {
        ForecastRunResponse response = new ForecastRunResponse();
        response.setRunId(run.getRunId());
        response.setStatus(run.getStatus());
        response.setIssuedAt(run.getIssuedAt());
        response.setDataCutoff(run.getDataCutoff());
        response.setForecastHorizonHours(run.getForecastHorizonHours());
        response.setNodeId(run.getNodeId());
        response.setModelVersion(run.getModelVersion());
        response.setArtifactChecksum(run.getArtifactChecksum());
        response.setWeatherIssuedAt(run.getWeatherIssuedAt());
        response.setWeatherFallbackReason(run.getWeatherFallbackReason());
        response.setPredictionCount(run.getPredictionCount());
        response.setCompletedAt(run.getCompletedAt());
        if (includeFailureReason) response.setFailureReason(run.getFailureReason());
        return response;
    }
}
