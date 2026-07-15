package com.powerload.controller;

import com.powerload.common.R;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.service.RealtimeLoadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 实时负荷数据接口
 *
 * <p>提供最近秒级实时数据的快照查询，用于 WebSocket 首次连接和断线重连时的数据补偿。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/data")
@RequiredArgsConstructor
public class RealtimeLoadController {

    private final RealtimeLoadService realtimeLoadService;

    /**
     * 查询最近 N 分钟的实时负荷数据快照
     *
     * @param minutes 查询分钟数，范围 1~60，默认 30
     * @return 按时间升序排列的实时数据点列表
     */
    @GetMapping("/realtime/recent")
    public R<List<RealtimeLoadPoint>> recent(
            @RequestParam(defaultValue = "30") int minutes) {

        if (minutes < 1 || minutes > 60) {
            throw new IllegalArgumentException("minutes 必须在 1~60 之间");
        }

        List<RealtimeLoadPoint> data = realtimeLoadService.getRecent(minutes);
        log.debug("实时快照查询: {} 分钟 → {} 条", minutes, data.size());
        return R.ok(data);
    }
}
