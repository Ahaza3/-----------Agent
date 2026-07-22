package com.powerload.scheduler;

import com.powerload.entity.GridNode;
import com.powerload.entity.LoadData;
import com.powerload.mapper.GridNodeMapper;
import com.powerload.mapper.LoadDataMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GridNodeSimulationServiceTest {

    private LoadDataMapper loadDataMapper;
    private GridNodeMapper gridNodeMapper;
    private GridNodeSimulationService service;
    private final List<LoadData> inserted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        loadDataMapper = mock(LoadDataMapper.class);
        gridNodeMapper = mock(GridNodeMapper.class);
        service = new GridNodeSimulationService(loadDataMapper, gridNodeMapper);
        inserted.clear();

        when(gridNodeMapper.selectList(any())).thenReturn(List.of(
                node(1L, "REGION-DEMO", "REGION", null, 1f),
                node(2L, "SUBSTATION-EAST", "SUBSTATION", 1L, 0.55f),
                node(3L, "SUBSTATION-WEST", "SUBSTATION", 1L, 0.45f),
                node(4L, "FEEDER-E-01", "FEEDER", 2L, 0.5f),
                node(5L, "FEEDER-E-02", "FEEDER", 2L, 0.5f),
                node(6L, "FEEDER-W-01", "FEEDER", 3L, 0.6f),
                node(7L, "FEEDER-W-02", "FEEDER", 3L, 0.4f)
        ));
        when(loadDataMapper.insert(any(LoadData.class))).thenAnswer(invocation -> {
            inserted.add(invocation.getArgument(0, LoadData.class));
            return 1;
        });
    }

    @Test
    void shouldGenerateIndependentFeederLoadsAndAggregateParents() {
        LocalDateTime time = LocalDateTime.of(2026, 7, 22, 18, 0);
        LoadData root = root(time, 1200f);
        when(loadDataMapper.selectOne(any())).thenReturn(root);
        when(loadDataMapper.selectList(any())).thenReturn(List.of(root));

        service.feed();

        List<LoadData> feeders = inserted.stream()
                .filter(row -> row.getNodeId() >= 4)
                .filter(row -> row.getNodeId() <= 7)
                .toList();
        assertTrue(feeders.size() == 4);
        assertNotEquals(feeders.get(0).getLoadMw(), feeders.get(1).getLoadMw());
        assertTrue(inserted.stream().anyMatch(row -> row.getNodeId() == 2L
                && "TOPOLOGY_AGGREGATED".equals(row.getDataSource())));
        assertTrue(inserted.stream().anyMatch(row -> row.getNodeId() == 3L
                && "TOPOLOGY_AGGREGATED".equals(row.getDataSource())));
        assertTrue(inserted.stream().anyMatch(row -> row.getNodeId() == 4L
                && "NODE_SIMULATION_ANOMALY".equals(row.getDataSource())));
    }

    private LoadData root(LocalDateTime time, float load) {
        LoadData row = new LoadData();
        row.setNodeId(1L);
        row.setTime(time);
        row.setLoadMw(load);
        row.setTemperature(35f);
        row.setHumidity(50f);
        row.setHour(time.getHour());
        row.setDayOfWeek(MockLoadProfile.dayOfWeek(time));
        row.setMonth(time.getMonthValue());
        row.setIsHoliday(MockLoadProfile.isHolidayInt(time));
        return row;
    }

    private GridNode node(Long id, String code, String type, Long parentId, Float ratio) {
        GridNode node = new GridNode();
        node.setId(id);
        node.setNodeCode(code);
        node.setNodeName(code);
        node.setNodeType(type);
        node.setParentId(parentId);
        node.setAllocationRatio(ratio);
        node.setStatus("IN_SERVICE");
        node.setSortOrder(id.intValue());
        return node;
    }
}
