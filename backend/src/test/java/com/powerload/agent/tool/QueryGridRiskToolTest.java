package com.powerload.agent.tool;

import com.powerload.agent.ToolResult;
import com.powerload.dto.response.GridRiskSnapshot;
import com.powerload.service.GridTopologyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryGridRiskToolTest {

    private GridTopologyService gridTopologyService;
    private QueryGridRiskTool tool;

    @BeforeEach
    void setUp() {
        gridTopologyService = mock(GridTopologyService.class);
        tool = new QueryGridRiskTool(gridTopologyService);
    }

    @Test
    void shouldFilterRiskByNodeType() {
        GridRiskSnapshot substation = snapshot("SUBSTATION-EAST", "SUBSTATION", "ORANGE");
        GridRiskSnapshot feeder = snapshot("FEEDER-E-01", "FEEDER", "RED");
        when(gridTopologyService.getRiskSnapshot()).thenReturn(List.of(substation, feeder));

        ToolResult result = tool.execute("{\"nodeType\":\"FEEDER\"}");

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) result.getData();
        @SuppressWarnings("unchecked")
        var nodes = (List<GridRiskSnapshot>) data.get("nodes");
        assertEquals(1, nodes.size());
        assertEquals("FEEDER-E-01", nodes.get(0).getNodeCode());
        assertEquals(true, data.get("simulated"));
    }

    @Test
    void shouldRejectInvalidJson() {
        ToolResult result = tool.execute("{");

        assertTrue(!result.isSuccess());
        assertTrue(result.getMessage().contains("参数无效"));
    }

    private GridRiskSnapshot snapshot(String code, String type, String level) {
        GridRiskSnapshot snapshot = new GridRiskSnapshot();
        snapshot.setNodeCode(code);
        snapshot.setNodeType(type);
        snapshot.setRiskLevel(level);
        return snapshot;
    }
}
