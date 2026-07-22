package com.powerload.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.common.GridTopologyConstants;
import com.powerload.entity.GridNode;
import com.powerload.entity.LoadData;
import com.powerload.mapper.GridNodeMapper;
import com.powerload.mapper.LoadDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 拓扑节点模拟数据馈送器。
 *
 * <p>根区域仍由 {@link MockDataFeeder} 负责补齐；本组件把根区域历史拆分为
 * 具有独立曲线和异常模式的馈线数据，再按拓扑向上汇总到变电站。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GridNodeSimulationService {

    private static final int HISTORY_WINDOW_HOURS = 200;
    private static final double MIN_LOAD_MW = 1.0;

    private final LoadDataMapper loadDataMapper;
    private final GridNodeMapper gridNodeMapper;

    private volatile LocalDateTime processedRootTime;

    @Scheduled(fixedRate = 15_000)
    public synchronized void feed() {
        try {
            LoadData latestRoot = latestRoot();
            if (latestRoot == null || latestRoot.getTime() == null || latestRoot.getLoadMw() == null) {
                return;
            }

            List<GridNode> nodes = activeNodes();
            List<GridNode> feeders = nodes.stream()
                    .filter(node -> "FEEDER".equals(node.getNodeType()))
                    .toList();
            if (feeders.isEmpty()) {
                return;
            }

            LocalDateTime start = processedRootTime == null
                    ? latestRoot.getTime().minusHours(HISTORY_WINDOW_HOURS - 1L)
                    : processedRootTime.plusHours(1);
            if (start.isAfter(latestRoot.getTime())) {
                return;
            }

            List<LoadData> roots = loadDataMapper.selectList(new LambdaQueryWrapper<LoadData>()
                    .eq(LoadData::getNodeId, GridTopologyConstants.ROOT_NODE_ID)
                    .ge(LoadData::getTime, start)
                    .le(LoadData::getTime, latestRoot.getTime())
                    .orderByAsc(LoadData::getTime));
            if (roots.isEmpty()) {
                return;
            }

            Map<Long, List<GridNode>> childrenByParent = childrenByParent(nodes);
            Map<Long, GridNode> nodeById = nodes.stream()
                    .collect(java.util.stream.Collectors.toMap(GridNode::getId, node -> node));
            for (LoadData root : roots) {
                feedOneHour(root, feeders, childrenByParent, nodeById);
            }
            processedRootTime = roots.get(roots.size() - 1).getTime();
            log.info("节点模拟数据已同步: {} 个根区域小时, {} 条馈线", roots.size(), feeders.size());
        } catch (Exception e) {
            log.error("节点模拟数据同步失败", e);
        }
    }

    private LoadData latestRoot() {
        return loadDataMapper.selectOne(new LambdaQueryWrapper<LoadData>()
                .eq(LoadData::getNodeId, GridTopologyConstants.ROOT_NODE_ID)
                .orderByDesc(LoadData::getTime)
                .last("LIMIT 1"));
    }

    private List<GridNode> activeNodes() {
        return gridNodeMapper.selectList(new LambdaQueryWrapper<GridNode>()
                .eq(GridNode::getStatus, "IN_SERVICE")
                .orderByAsc(GridNode::getSortOrder)
                .orderByAsc(GridNode::getId));
    }

    private Map<Long, List<GridNode>> childrenByParent(List<GridNode> nodes) {
        Map<Long, List<GridNode>> result = new HashMap<>();
        for (GridNode node : nodes) {
            if (node.getParentId() != null) {
                result.computeIfAbsent(node.getParentId(), ignored -> new ArrayList<>()).add(node);
            }
        }
        result.values().forEach(children -> children.sort(
                Comparator.comparing(GridNode::getSortOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(GridNode::getId)));
        return result;
    }

    private void feedOneHour(LoadData root,
                             List<GridNode> feeders,
                             Map<Long, List<GridNode>> childrenByParent,
                             Map<Long, GridNode> nodeById) {
        Map<Long, Double> rawLoads = new LinkedHashMap<>();
        double rawTotal = 0;
        for (GridNode feeder : feeders) {
            double ratio = feeder.getAllocationRatio() == null
                    ? 1.0 / feeders.size()
                    : Math.max(0.01, feeder.getAllocationRatio());
            double multiplier = anomalyMultiplier(feeder.getNodeCode(), root.getTime());
            double noise = localNoise(feeder.getNodeCode(), root.getTime());
            double raw = Math.max(MIN_LOAD_MW,
                    root.getLoadMw() * ratio * multiplier * (1.0 + noise));
            rawLoads.put(feeder.getId(), raw);
            rawTotal += raw;
        }

        double scale = rawTotal <= 0 ? 1.0 : root.getLoadMw() / rawTotal;
        Map<Long, Double> loads = new LinkedHashMap<>();
        for (GridNode feeder : feeders) {
            double load = Math.max(MIN_LOAD_MW, rawLoads.get(feeder.getId()) * scale);
            loads.put(feeder.getId(), load);
            insertIfAbsent(toLoadData(root, feeder, load,
                    isAnomaly(feeder.getNodeCode(), root.getTime())
                            ? "NODE_SIMULATION_ANOMALY" : "NODE_SIMULATION"));
        }

        List<GridNode> parents = nodeById.values().stream()
                .filter(node -> !"FEEDER".equals(node.getNodeType()))
                .sorted(Comparator.comparingInt(this::depth).reversed())
                .toList();
        for (GridNode parent : parents) {
            double aggregate = childrenByParent.getOrDefault(parent.getId(), List.of()).stream()
                    .mapToDouble(child -> aggregate(child, childrenByParent, loads))
                    .sum();
            if (aggregate > 0 && GridTopologyConstants.ROOT_NODE_ID != parent.getId()) {
                insertIfAbsent(toLoadData(root, parent, aggregate, "TOPOLOGY_AGGREGATED"));
            }
            loads.put(parent.getId(), aggregate);
        }
    }

    private double aggregate(GridNode node,
                             Map<Long, List<GridNode>> childrenByParent,
                             Map<Long, Double> loads) {
        if (loads.containsKey(node.getId())) {
            return loads.get(node.getId());
        }
        double total = childrenByParent.getOrDefault(node.getId(), List.of()).stream()
                .mapToDouble(child -> aggregate(child, childrenByParent, loads))
                .sum();
        loads.put(node.getId(), total);
        return total;
    }

    private LoadData toLoadData(LoadData root, GridNode node, double load, String source) {
        LoadData row = new LoadData();
        row.setNodeId(node.getId());
        row.setTime(root.getTime());
        row.setLoadMw((float) load);
        double weatherNoise = localNoise(node.getNodeCode() + ":weather", root.getTime());
        row.setTemperature(root.getTemperature() == null
                ? null : (float) (root.getTemperature() + weatherNoise * 4));
        row.setHumidity(root.getHumidity() == null
                ? null : (float) Math.max(0, Math.min(100, root.getHumidity() + weatherNoise * 8)));
        row.setHour(root.getHour());
        row.setDayOfWeek(root.getDayOfWeek());
        row.setMonth(root.getMonth());
        row.setIsHoliday(root.getIsHoliday());
        row.setDataSource(source);
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }

    private void insertIfAbsent(LoadData row) {
        try {
            loadDataMapper.insert(row);
        } catch (DuplicateKeyException e) {
            log.debug("节点小时数据已存在: nodeId={}, time={}", row.getNodeId(), row.getTime());
        }
    }

    private int depth(GridNode node) {
        if ("REGION".equals(node.getNodeType())) return 0;
        if ("SUBSTATION".equals(node.getNodeType())) return 1;
        return 2;
    }

    private boolean isAnomaly(String nodeCode, LocalDateTime time) {
        return anomalyMultiplier(nodeCode, time) >= 1.35;
    }

    private double anomalyMultiplier(String nodeCode, LocalDateTime time) {
        int hour = time.getHour();
        int day = time.getDayOfWeek().getValue();
        if ("FEEDER-E-01".equals(nodeCode) && day <= 5 && hour >= 17 && hour <= 20) {
            return 1.75;
        }
        if ("FEEDER-E-02".equals(nodeCode) && hour >= 7 && hour <= 9) {
            return 1.25;
        }
        if ("FEEDER-W-01".equals(nodeCode) && day <= 5 && hour >= 10 && hour <= 12) {
            return 1.45;
        }
        if ("FEEDER-W-02".equals(nodeCode) && hour >= 13 && hour <= 15) {
            return 1.75;
        }
        return 1.0;
    }

    private double localNoise(String nodeCode, LocalDateTime time) {
        long hour = time.atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond() / 3600;
        double phase = Math.abs(nodeCode.hashCode() % 360) * Math.PI / 180.0;
        return Math.sin(hour * 0.73 + phase) * 0.045;
    }
}
