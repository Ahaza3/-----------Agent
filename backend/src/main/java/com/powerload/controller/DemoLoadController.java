package com.powerload.controller;

import com.powerload.common.R;
import com.powerload.dto.response.RealtimeLoadStatus;
import com.powerload.service.RealtimeLoadService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 开发环境负荷异常演示控制，不在生产环境注册。 */
@Profile("dev")
@ConditionalOnProperty(prefix = "demo", name = "enabled", havingValue = "true")
@RestController
@RequestMapping("/api/v1/demo/load")
@RequiredArgsConstructor
public class DemoLoadController {

    private final RealtimeLoadService realtimeLoadService;

    @GetMapping("/status")
    public R<RealtimeLoadStatus> status() {
        return R.ok(realtimeLoadService.getStatus());
    }

    @PostMapping("/normal")
    public R<RealtimeLoadStatus> normal() {
        return R.ok(realtimeLoadService.enterNormalMode());
    }

    @PostMapping("/spike")
    public R<RealtimeLoadStatus> spike() {
        return R.ok(realtimeLoadService.startSpikeDemo());
    }

    @PostMapping("/recover")
    public R<RealtimeLoadStatus> recover() {
        return R.ok(realtimeLoadService.startRecoveryDemo());
    }
}
