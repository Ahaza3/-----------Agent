package com.powerload.scheduler;

import com.powerload.entity.LoadData;
import com.powerload.service.LoadDataService;
import com.powerload.websocket.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 实时负荷推送 — 每秒查询最新数据并广播给前端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadScheduler {

    private final LoadDataService loadDataService;
    private final PushService pushService;

    @Scheduled(fixedRate = 60_000) // 兜底，主推送在 MockDataFeeder
    public void pushLatestLoad() {
        try {
            LoadData latest = loadDataService.getLatest();
            if (latest != null) {
                pushService.pushLoad(latest);
            }
        } catch (Exception e) {
            log.error("负荷推送异常", e);
        }
    }
}
