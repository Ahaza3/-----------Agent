package com.powerload.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.dto.response.ForecastResponse;
import com.powerload.entity.GridNode;
import com.powerload.mapper.GridNodeMapper;
import com.powerload.service.PredictService;
import com.powerload.websocket.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 预测调度 — 每小时触发一次预测，结果通过 WebSocket 推送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PredictScheduler {

    private final PredictService predictService;
    private final PushService pushService;
    private final GridNodeMapper gridNodeMapper;

    @Scheduled(fixedRate = 3_600_000) // 1 小时
    public void pushPrediction() {
        try {
            List<Long> nodeIds = gridNodeMapper.selectList(new LambdaQueryWrapper<GridNode>()
                            .eq(GridNode::getStatus, "IN_SERVICE")
                            .orderByAsc(GridNode::getSortOrder)
                            .orderByAsc(GridNode::getId))
                    .stream()
                    .map(GridNode::getId)
                    .toList();
            if (nodeIds.isEmpty()) {
                nodeIds = List.of(com.powerload.common.GridTopologyConstants.ROOT_NODE_ID);
            }

            Map<Long, ForecastResponse> forecasts = predictService.forecastNodes(nodeIds);
            forecasts.values().forEach(pushService::pushPrediction);
            log.info("定时节点预测完成: {}/{} nodes", forecasts.size(), nodeIds.size());
        } catch (Exception e) {
            log.error("预测调度异常", e);
        }
    }
}
