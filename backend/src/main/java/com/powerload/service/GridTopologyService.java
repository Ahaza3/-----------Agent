package com.powerload.service;

import com.powerload.dto.request.GridScenarioRequest;
import com.powerload.dto.response.GridRiskSnapshot;
import com.powerload.dto.response.GridScenarioResponse;
import com.powerload.dto.response.GridTopologyResponse;

import java.util.List;

/**
 * 电网拓扑查询与派生风险服务。
 */
public interface GridTopologyService {

    /**
     * 查询当前拓扑结构。
     */
    GridTopologyResponse getTopology();

    /**
     * 按节点计算当前和预测峰值风险。
     *
     * <p>当前结果来自单区域模拟负荷按拓扑容量比例派生，
     * 直到接入真实节点数据前不得视为 SCADA 或潮流结果。</p>
     */
    List<GridRiskSnapshot> getRiskSnapshot();

    /**
     * 只读故障场景推演：计算受影响节点、可转供余量和预计未供负荷。
     */
    GridScenarioResponse simulateScenario(GridScenarioRequest request);

    /**
     * 返回去重后的告警根节点。
     */
    default List<GridRiskSnapshot> getAlertRoots() {
        return getRiskSnapshot().stream()
                .filter(snapshot -> snapshot.getAlertRootNodeCode() != null)
                .filter(snapshot -> snapshot.getNodeCode().equals(snapshot.getAlertRootNodeCode()))
                .toList();
    }
}
