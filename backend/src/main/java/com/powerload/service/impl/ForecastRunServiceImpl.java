package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerload.entity.ForecastRun;
import com.powerload.mapper.ForecastRunMapper;
import com.powerload.service.ForecastRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ForecastRunServiceImpl implements ForecastRunService {

    private final ForecastRunMapper forecastRunMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ForecastRun createOrReuseCompleted(ForecastRun run) {
        if (run.getIdempotencyKey() != null && !run.getIdempotencyKey().isBlank()) {
            ForecastRun completed = forecastRunMapper.selectOne(new LambdaQueryWrapper<ForecastRun>()
                    .eq(ForecastRun::getIdempotencyKey, run.getIdempotencyKey())
                    .eq(ForecastRun::getStatus, "COMPLETED")
                    .last("LIMIT 1"));
            if (completed != null) {
                return completed;
            }
        }
        forecastRunMapper.insert(run);
        return run;
    }

    @Override
    public void updateMetadata(ForecastRun run) {
        forecastRunMapper.updateById(run);
    }

    @Override
    public void markCompleted(Long id, int predictionCount, LocalDateTime completedAt) {
        ForecastRun update = new ForecastRun();
        update.setId(id);
        update.setStatus("COMPLETED");
        update.setPredictionCount(predictionCount);
        update.setCompletedAt(completedAt);
        forecastRunMapper.updateById(update);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id, String failureReason) {
        if (id == null) {
            return;
        }
        ForecastRun update = new ForecastRun();
        update.setId(id);
        update.setStatus("FAILED");
        update.setFailureReason(failureReason);
        update.setCompletedAt(LocalDateTime.now());
        forecastRunMapper.updateById(update);
    }

    @Override
    public Page<ForecastRun> page(Long nodeId, String status, LocalDateTime start, LocalDateTime end,
                                   int page, int size) {
        return forecastRunMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<ForecastRun>()
                .eq(nodeId != null, ForecastRun::getNodeId, nodeId)
                .eq(status != null && !status.isBlank(), ForecastRun::getStatus, status)
                .ge(start != null, ForecastRun::getIssuedAt, start)
                .le(end != null, ForecastRun::getIssuedAt, end)
                .orderByDesc(ForecastRun::getIssuedAt));
    }
}
