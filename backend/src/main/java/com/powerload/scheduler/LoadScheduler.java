package com.powerload.scheduler;

import com.powerload.entity.LoadData;
import com.powerload.service.LoadDataService;
import com.powerload.websocket.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * 实时负荷推送 — 每 5 秒读取最新 DB 值 + 微调 → WebSocket 推送
 *
 * <p>不插入 DB，只推送当前读数，图表数据保持小时粒度不变。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadScheduler {

    private final LoadDataService loadDataService;
    private final PushService pushService;
    private final Random random = new Random();

    @Scheduled(fixedRate = 5_000)
    public void pushLatestLoad() {
        try {
            LoadData latest = loadDataService.getLatest();
            if (latest == null || latest.getLoadMw() == null) return;

            // 基于最新 DB 值做一个小的随机扰动（模拟"实时"波动感）
            float baseLoad = latest.getLoadMw();
            float jitter = (float) random.nextGaussian() * 3f;
            float liveLoad = Math.max(0, baseLoad + jitter);

            // 构造一个虚拟 LoadData 用于推送（不写库）
            LoadData live = new LoadData();
            live.setTime(LocalDateTime.now());
            live.setLoadMw(liveLoad);
            live.setTemperature(latest.getTemperature());
            live.setHumidity(latest.getHumidity());
            live.setHour(LocalDateTime.now().getHour());

            pushService.pushLoad(live);
        } catch (Exception e) {
            log.error("Load push error", e);
        }
    }
}
