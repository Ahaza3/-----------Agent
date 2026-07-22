package com.powerload.controller;

import com.powerload.common.R;
import com.powerload.dto.request.GridScenarioRequest;
import com.powerload.dto.response.GridRiskSnapshot;
import com.powerload.dto.response.GridScenarioResponse;
import com.powerload.dto.response.GridTopologyResponse;
import com.powerload.service.GridTopologyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 电网拓扑与节点风险接口。
 */
@RestController
@RequestMapping("/api/v1/topology")
@RequiredArgsConstructor
public class GridTopologyController {

    private final GridTopologyService gridTopologyService;

    @GetMapping
    public R<GridTopologyResponse> topology() {
        return R.ok(gridTopologyService.getTopology());
    }

    @GetMapping("/risk")
    public R<List<GridRiskSnapshot>> risk() {
        return R.ok(gridTopologyService.getRiskSnapshot());
    }

    @PostMapping("/scenario")
    public R<GridScenarioResponse> scenario(@RequestBody GridScenarioRequest request) {
        return R.ok(gridTopologyService.simulateScenario(request));
    }
}
