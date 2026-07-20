package com.powerload.scheduler;

import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.LoadData;
import com.powerload.service.LoadDataService;
import com.powerload.service.RealtimeLoadService;
import com.powerload.websocket.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 实时负荷推送 — 每秒从 RealtimeLoadService 生成新点 → WebSocket 推送
 *
 * <p>启动时从 DB 最新小时数据初始化模拟器状态，之后每秒生成有状态均值回归随机游走。
 * 不插入 DB，仅推送实时点到 /topic/load。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadScheduler {

    private final LoadDataService loadDataService;
    private final RealtimeLoadService realtimeLoadService;
    private final PushService pushService;

    private volatile boolean initialized = false;

    /**
     * 每秒执行：生成实时点 → 推送 → 更新模拟器目标负荷（跟随小时级最新值）
     */
    @Scheduled(fixedRate = 1_000)
    public void pushLatestLoad() {
        try {
            // 首次执行时从 DB 初始化
            if (!initialized) {
                initFromDb();
            }

            // 定期同步目标负荷（每分钟），遵循小时级趋势
            syncTargetLoad();

            RealtimeLoadPoint point = realtimeLoadService.generateAndAppend();
            pushService.pushRealtimeLoad(point);
        } catch (Exception e) {
            log.error("Load push error", e);
        }
    }

    private void initFromDb() {
        LoadData latest = loadDataService.getLatestHourly();
        if (latest == null || latest.getLoadMw() == null) {
            log.warn("DB 无小时级数据，使用默认值初始化实时模拟器");
            realtimeLoadService.initialize(800, 25, 60);
        } else {
            float temp = latest.getTemperature() != null ? latest.getTemperature() : 25f;
            float hum = latest.getHumidity() != null ? latest.getHumidity() : 60f;
            realtimeLoadService.initialize(latest.getLoadMw(), temp, hum);
        }
        initialized = true;
    }

    private long lastTargetSync = 0;

    private void syncTargetLoad() {
        long now = System.currentTimeMillis();
        if (now - lastTargetSync < 60_000) return; // 每分钟刷新一次
        lastTargetSync = now;

        LoadData latest = loadDataService.getLatestHourly();
        if (latest != null && latest.getLoadMw() != null) {
            realtimeLoadService.setTargetLoad(latest.getLoadMw());
        }
    }
}
