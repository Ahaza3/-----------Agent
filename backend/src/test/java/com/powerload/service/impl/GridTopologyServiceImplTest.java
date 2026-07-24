package com.powerload.service.impl;

import com.powerload.dto.response.GridRiskSnapshot;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.dto.request.GridScenarioRequest;
import com.powerload.entity.GridEdge;
import com.powerload.entity.GridNode;
import com.powerload.entity.LoadData;
import com.powerload.entity.PredictionResult;
import com.powerload.mapper.GridEdgeMapper;
import com.powerload.mapper.GridNodeMapper;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.mapper.PredictionResultMapper;
import com.powerload.service.LoadDataService;
import com.powerload.service.RealtimeLoadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GridTopologyServiceImplTest {

    private GridNodeMapper gridNodeMapper;
    private GridEdgeMapper gridEdgeMapper;
    private LoadDataService loadDataService;
    private RealtimeLoadService realtimeLoadService;
    private PredictionResultMapper predictionResultMapper;
    private GridTopologyServiceImpl service;

    @BeforeEach
    void setUp() {
        gridNodeMapper = mock(GridNodeMapper.class);
        gridEdgeMapper = mock(GridEdgeMapper.class);
        loadDataService = mock(LoadDataService.class);
        realtimeLoadService = mock(RealtimeLoadService.class);
        predictionResultMapper = mock(PredictionResultMapper.class);
        service = new GridTopologyServiceImpl(
                gridNodeMapper, gridEdgeMapper, loadDataService, realtimeLoadService, predictionResultMapper);

        when(gridNodeMapper.selectList(any())).thenReturn(List.of(
                node(1L, "REGION-DEMO", "REGION", null, 1f, 1600f),
                node(2L, "SUBSTATION-EAST", "SUBSTATION", 1L, 0.55f, 900f),
                node(3L, "FEEDER-E-01", "FEEDER", 2L, 0.5f, 450f),
                node(4L, "FEEDER-E-02", "FEEDER", 2L, 0.5f, 450f),
                node(5L, "SUBSTATION-WEST", "SUBSTATION", 1L, 0.45f, 700f)
        ));
        when(gridEdgeMapper.selectList(any())).thenReturn(List.of(
                edge(1L, 2L), edge(1L, 5L), edge(2L, 3L)));

        RealtimeLoadPoint latest = new RealtimeLoadPoint();
        latest.setLoadMw(1300f);
        when(realtimeLoadService.getLatestForAlert()).thenReturn(latest);

        PredictionResult marker = new PredictionResult();
        marker.setCreatedAt(LocalDateTime.of(2026, 7, 22, 18, 0));
        when(predictionResultMapper.selectOne(any())).thenReturn(marker);
        PredictionResult forecast = new PredictionResult();
        forecast.setPredictedLoad(1500f);
        when(predictionResultMapper.selectList(any())).thenReturn(List.of(forecast));
    }

    @Test
    void shouldAggregateRootLoadAcrossChildrenAndRankOverloadRisk() {
        List<GridRiskSnapshot> snapshots = service.getRiskSnapshot();

        GridRiskSnapshot root = snapshots.stream()
                .filter(item -> "REGION-DEMO".equals(item.getNodeCode()))
                .findFirst()
                .orElseThrow();
        GridRiskSnapshot feeder = snapshots.stream()
                .filter(item -> "FEEDER-E-01".equals(item.getNodeCode()))
                .findFirst()
                .orElseThrow();

        assertEquals(1300f, root.getCurrentLoadMw());
        assertEquals(1500f, root.getForecastPeakMw());
        assertEquals(412.5f, feeder.getForecastPeakMw());
        assertEquals("YELLOW", feeder.getRiskLevel());
        assertTrue(feeder.isSimulated());
        assertEquals("DERIVED_SIMULATION", feeder.getSource());
        assertTrue(feeder.isAlertDeduplicated());
        assertEquals("REGION-DEMO", feeder.getAlertRootNodeCode());
    }

    @Test
    void shouldScaleTerminalLoadsWithRealtimeRootSpike() {
        LoadDataMapper liveLoadMapper = mock(LoadDataMapper.class);
        AtomicInteger callIndex = new AtomicInteger();
        when(liveLoadMapper.selectOne(any())).thenAnswer(invocation -> {
            float[] baselineLoads = {300f, 250f, 250f, 200f};
            LoadData row = new LoadData();
            row.setLoadMw(baselineLoads[callIndex.getAndIncrement()]);
            return row;
        });
        when(predictionResultMapper.selectOne(any())).thenReturn(null);
        when(realtimeLoadService.getLatestForAlert()).thenAnswer(invocation -> {
            RealtimeLoadPoint latest = new RealtimeLoadPoint();
            latest.setLoadMw(1800f);
            return latest;
        });

        service = new GridTopologyServiceImpl(
                gridNodeMapper, gridEdgeMapper, loadDataService, realtimeLoadService,
                predictionResultMapper, liveLoadMapper);

        List<GridRiskSnapshot> snapshots = service.getRiskSnapshot();
        GridRiskSnapshot root = snapshots.stream()
                .filter(item -> "REGION-DEMO".equals(item.getNodeCode()))
                .findFirst()
                .orElseThrow();
        GridRiskSnapshot feeder = snapshots.stream()
                .filter(item -> "FEEDER-E-01".equals(item.getNodeCode()))
                .findFirst()
                .orElseThrow();

        assertEquals(1800f, root.getCurrentLoadMw());
        assertEquals(642.9f, feeder.getCurrentLoadMw(), 0.1f);
        assertEquals("RED", feeder.getRiskLevel());
    }

    @Test
    void shouldPreferDirectParentForecastOverSummedChildPeaks() {
        LoadDataMapper loadDataMapper = mock(LoadDataMapper.class);
        PredictionResult marker = new PredictionResult();
        marker.setCreatedAt(LocalDateTime.of(2026, 7, 22, 18, 0));
        PredictionResult forecast = new PredictionResult();
        forecast.setPredictedLoad(1500f);
        when(predictionResultMapper.selectOne(any())).thenReturn(marker);
        when(predictionResultMapper.selectList(any())).thenReturn(List.of(forecast));

        service = new GridTopologyServiceImpl(
                gridNodeMapper, gridEdgeMapper, loadDataService, realtimeLoadService,
                predictionResultMapper, loadDataMapper);

        List<GridRiskSnapshot> snapshots = service.getRiskSnapshot();
        GridRiskSnapshot root = snapshots.stream()
                .filter(item -> "REGION-DEMO".equals(item.getNodeCode()))
                .findFirst()
                .orElseThrow();
        GridRiskSnapshot east = snapshots.stream()
                .filter(item -> "SUBSTATION-EAST".equals(item.getNodeCode()))
                .findFirst()
                .orElseThrow();

        assertEquals(1500f, root.getForecastPeakMw());
        assertEquals(1500f, east.getForecastPeakMw());
    }

    @Test
    void shouldSimulateNodeFaultImpactAcrossDescendants() {
        GridScenarioRequest request = new GridScenarioRequest();
        request.setTargetType("NODE");
        request.setNodeId(2L);

        var result = service.simulateScenario(request);

        assertEquals("SUBSTATION-EAST", result.getTargetCode());
        assertEquals(3, result.getAffectedNodes().size());
        assertTrue(result.getAffectedNodes().stream()
                .anyMatch(node -> "FEEDER-E-01".equals(node.getNodeCode())));
        assertTrue(result.getAffectedLoadMw() > 0);
    }

    @Test
    void shouldReturnOnlyRootRiskForDeduplicatedAlertGroup() {
        List<GridRiskSnapshot> roots = service.getAlertRoots();

        assertTrue(roots.stream().anyMatch(snapshot -> "REGION-DEMO".equals(snapshot.getNodeCode())));
        assertTrue(roots.stream().noneMatch(snapshot -> "FEEDER-E-01".equals(snapshot.getNodeCode())));
    }

    @Test
    void shouldExposeFlatTopologyWithNodeAndEdgeCodes() {
        var topology = service.getTopology();

        assertTrue(topology.isSimulated());
        assertEquals("DERIVED_SIMULATION", topology.getSource());
        assertEquals(5, topology.getNodes().size());
        assertEquals("SUBSTATION-EAST", topology.getEdges().get(0).getToNodeCode());
    }

    private GridNode node(Long id, String code, String type, Long parentId,
                          Float allocationRatio, Float capacity) {
        GridNode node = new GridNode();
        node.setId(id);
        node.setNodeCode(code);
        node.setNodeName(code);
        node.setNodeType(type);
        node.setParentId(parentId);
        node.setAllocationRatio(allocationRatio);
        node.setRatedCapacityMw(capacity);
        node.setStatus("IN_SERVICE");
        node.setTopologyVersion("demo-v1");
        node.setSortOrder(id.intValue());
        return node;
    }

    private GridEdge edge(Long from, Long to) {
        GridEdge edge = new GridEdge();
        edge.setFromNodeId(from);
        edge.setToNodeId(to);
        edge.setStatus("IN_SERVICE");
        edge.setTopologyVersion("demo-v1");
        return edge;
    }
}
