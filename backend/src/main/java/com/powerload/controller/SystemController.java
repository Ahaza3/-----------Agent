package com.powerload.controller;

import com.powerload.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统管理接口 — 健康检查
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        return R.ok(Map.of(
                "status", "UP",
                "version", "1.0.0"
        ));
    }
}
