package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.common.GridTopologyConstants;
import com.powerload.dto.request.GridScenarioRequest;
import com.powerload.dto.response.GridEdgeView;
import com.powerload.dto.response.GridNodeView;
import com.powerload.dto.response.GridRiskSnapshot;
import com.powerload.dto.response.GridScenarioEdgeImpact;
import com.powerload.dto.response.GridScenarioNodeImpact;
import com.powerload.dto.response.GridScenarioResponse;
import com.powerload.dto.response.GridTopologyResponse;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.GridEdge;
import com.powerload.entity.GridNode;
import com.powerload.entity.LoadData;
import com.powerload.entity.PredictionResult;
import com.powerload.mapper.GridEdgeMapper;
import com.powerload.mapper.GridNodeMapper;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.mapper.PredictionResultMapper;
import com.powerload.service.GridTopologyService;
import com.powerload.service.LoadDataService;
import com.powerload.service.RealtimeLoadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 拓扑 MVP 服务实现。
 */
@Service
public class GridTopologyServiceImpl implements GridTopologyService {

    private static final String SOURCE = "DERIVED_SIMULATION";
    private static final String TOPOLOGY_VERSION = "demo-v1";
    private static final double MAX_SIMULATED_FORECAST_RATIO = 1.20;

    private final GridNodeMapper gridNodeMapper;
    private final GridEdgeMapper gridEdgeMapper;
    private final LoadDataService loadDataService;
    private final RealtimeLoadService realtimeLoadService;
    private final PredictionResultMapper predictionResultMapper;
    private final LoadDataMapper loadDataMapper;

    public GridTopologyServiceImpl(GridNodeMapper gridNodeMapper,
                                   GridEdgeMapper gridEdgeMapper,
                                   LoadDataService loadDataService,
                                   RealtimeLoadService realtimeLoadService,
                                   PredictionResultMapper predictionResultMapper) {
        this(gridNodeMapper, gridEdgeMapper, loadDataService, realtimeLoadService,
                predictionResultMapper, null);
    }

    @Autowired
    public GridTopologyServiceImpl(GridNodeMapper gridNodeMapper,
                                   GridEdgeMapper gridEdgeMapper,
                                   LoadDataService loadDataService,
                                   RealtimeLoadService realtimeLoadService,
                                   PredictionResultMapper predictionResultMapper,
                                   LoadDataMapper loadDataMapper) {
        this.gridNodeMapper = gridNodeMapper;
        this.gridEdgeMapper = gridEdgeMapper;
        this.loadDataService = loadDataService;
        this.realtimeLoadService = realtimeLoadService;
        this.predictionResultMapper = predictionResultMapper;
        this.loadDataMapper = loadDataMapper;
    }

    @Override
    public GridTopologyResponse getTopology() {
        List<GridNode> nodes = activeNodes();
        Map<Long, GridNode> byId = indexNodes(nodes);
        List<GridEdge> edges = gridEdgeMapper.selectList(new LambdaQueryWrapper<GridEdge>()
                .eq(GridEdge::getStatus, "IN_SERVICE")
                .orderByAsc(GridEdge::getId));

        GridTopologyResponse response = new GridTopologyResponse();
        response.setTopologyVersion(nodes.stream()
                .map(GridNode::getTopologyVersion)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(TOPOLOGY_VERSION));
        response.setSimulated(true);
        response.setSource(SOURCE);
        response.setNodes(nodes.stream().map(node -> toNodeView(node, byId)).toList());
        response.setEdges(edges.stream().map(edge -> toEdgeView(edge, byId)).toList());
        return response;
    }

    @Override
    public List<GridRiskSnapshot> getRiskSnapshot() {
        List<GridNode> nodes = activeNodes();
        if (nodes.isEmpty()) {
            return List.of();
        }

        Map<Long, GridNode> byId = indexNodes(nodes);
        Map<Long, List<GridNode>> childrenByParent = new HashMap<>();
        for (GridNode node : nodes) {
            if (node.getParentId() != null) {
                childrenByParent.computeIfAbsent(node.getParentId(), ignored -> new ArrayList<>()).add(node);
            }
        }
        childrenByParent.values().forEach(children -> children.sort(
                Comparator.comparing(GridNode::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(GridNode::getId)));

        double currentRootLoad = currentRootLoad();
        Map<Long, Double> currentLoads = currentLoads(nodes, currentRootLoad);
        Map<Long, ForecastPeak> forecastPeaks = latestForecastPeaks(nodes, currentLoads);
        ForecastPeak rootForecast = forecastPeaks.getOrDefault(
                GridTopologyConstants.ROOT_NODE_ID, new ForecastPeak(0, false, false));
        double forecastRootLoad = rootForecast.available ? rootForecast.loadMw : currentRootLoad;

        Map<Long, NodeMetrics> metrics = new LinkedHashMap<>();
        for (GridNode root : nodes.stream()
                .filter(node -> node.getParentId() == null)
                .sorted(Comparator.comparing(GridNode::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList()) {
            calculateMetrics(root, currentRootLoad, forecastRootLoad, rootForecast.available,
                    childrenByParent, currentLoads, forecastPeaks, metrics);
        }

        List<GridRiskSnapshot> snapshots = nodes.stream()
                .map(node -> toRiskSnapshot(node, byId, metrics.get(node.getId()),
                        metrics.get(node.getId()) != null && metrics.get(node.getId()).forecastAvailable))
                .sorted(Comparator.comparingInt((GridRiskSnapshot snapshot) -> riskRank(snapshot.getRiskLevel()))
                        .reversed()
                        .thenComparing(GridRiskSnapshot::getNodeCode))
                .toList();
        markAlertRoots(snapshots, byId);
        return snapshots;
    }

    @Override
    public GridScenarioResponse simulateScenario(GridScenarioRequest request) {
        if (request == null || request.getTargetType() == null) {
            throw new IllegalArgumentException("场景目标不能为空");
        }

        List<GridNode> nodes = activeNodes();
        List<GridEdge> edges = gridEdgeMapper.selectList(new LambdaQueryWrapper<GridEdge>()
                .eq(GridEdge::getStatus, "IN_SERVICE")
                .orderByAsc(GridEdge::getId));
        Map<Long, GridNode> nodeById = indexNodes(nodes);
        Map<Long, List<GridNode>> childrenByParent = new HashMap<>();
        for (GridNode node : nodes) {
            if (node.getParentId() != null) {
                childrenByParent.computeIfAbsent(node.getParentId(), ignored -> new ArrayList<>()).add(node);
            }
        }

        String targetType = request.getTargetType().trim().toUpperCase();
        Set<Long> affectedIds = new LinkedHashSet<>();
        Long targetId;
        String targetCode;
        String targetName;
        GridEdge failedEdge = null;
        if ("NODE".equals(targetType)) {
            targetId = request.getNodeId();
            GridNode target = nodeById.get(targetId);
            if (target == null) {
                throw new IllegalArgumentException("场景节点不存在: " + targetId);
            }
            targetCode = target.getNodeCode();
            targetName = target.getNodeName();
            collectDescendants(target.getId(), childrenByParent, affectedIds);
        } else if ("EDGE".equals(targetType)) {
            targetId = request.getEdgeId();
            failedEdge = edges.stream()
                    .filter(edge -> edge.getId().equals(targetId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("场景线路不存在: " + targetId));
            GridNode target = nodeById.get(failedEdge.getToNodeId());
            if (target == null) {
                throw new IllegalArgumentException("场景线路终点不存在: " + failedEdge.getToNodeId());
            }
            targetCode = target.getNodeCode();
            targetName = target.getNodeName();
            collectDescendants(target.getId(), childrenByParent, affectedIds);
        } else {
            throw new IllegalArgumentException("targetType 仅支持 NODE 或 EDGE");
        }

        List<GridRiskSnapshot> risk = getRiskSnapshot();
        Map<Long, GridRiskSnapshot> riskById = risk.stream()
                .collect(java.util.stream.Collectors.toMap(GridRiskSnapshot::getNodeId, snapshot -> snapshot));
        List<GridScenarioNodeImpact> impactedNodes = nodes.stream()
                .filter(node -> affectedIds.contains(node.getId()))
                .map(node -> toScenarioNodeImpact(node, riskById.get(node.getId())))
                .toList();

        Set<Long> leafIds = nodes.stream()
                .filter(node -> affectedIds.contains(node.getId()))
                .filter(node -> childrenByParent.getOrDefault(node.getId(), List.of()).stream()
                        .noneMatch(child -> affectedIds.contains(child.getId())))
                .map(GridNode::getId)
                .collect(java.util.stream.Collectors.toSet());
        double affectedLoad = impactedNodes.stream()
                .filter(node -> leafIds.contains(node.getNodeId()))
                .mapToDouble(node -> node.getCurrentLoadMw() == null ? 0 : node.getCurrentLoadMw())
                .sum();

        double transferableHeadroom = nodes.stream()
                .filter(node -> !affectedIds.contains(node.getId()))
                .filter(node -> childrenByParent.getOrDefault(node.getId(), List.of()).isEmpty())
                .map(riskById::get)
                .filter(Objects::nonNull)
                .mapToDouble(snapshot -> snapshot.getRatedCapacityMw() == null
                        || snapshot.getCurrentLoadMw() == null
                        ? 0
                        : Math.max(0, snapshot.getRatedCapacityMw() - snapshot.getCurrentLoadMw()))
                .sum();
        if (failedEdge != null && failedEdge.getCapacityMw() != null) {
            transferableHeadroom = Math.min(transferableHeadroom, failedEdge.getCapacityMw());
        }
        double unservedLoad = Math.max(0, affectedLoad - transferableHeadroom);
        GridRiskSnapshot rootRisk = riskById.get(GridTopologyConstants.ROOT_NODE_ID);
        double baselineLoad = rootRisk != null && rootRisk.getCurrentLoadMw() != null
                ? rootRisk.getCurrentLoadMw() : 0;

        GridScenarioResponse response = new GridScenarioResponse();
        response.setTargetType(targetType);
        response.setTargetId(targetId);
        response.setTargetCode(targetCode);
        response.setTargetName(targetName);
        response.setBaselineLoadMw((float) round(baselineLoad));
        response.setAffectedLoadMw((float) round(affectedLoad));
        response.setTransferableHeadroomMw((float) round(transferableHeadroom));
        response.setUnservedLoadMw((float) round(unservedLoad));
        response.setSeverity(unservedLoad > 0 ? "RED" : affectedLoad > 0 ? "ORANGE" : "NORMAL");
        response.setSimulated(true);
        response.setSource("TOPOLOGY_SCENARIO_SIMULATION");
        response.setAffectedNodes(impactedNodes);
        GridEdge scenarioEdge = failedEdge;
        response.setAffectedEdges(edges.stream()
                .filter(edge -> affectedIds.contains(edge.getToNodeId())
                        || affectedIds.contains(edge.getFromNodeId()))
                .map(edge -> toScenarioEdgeImpact(edge, nodeById, scenarioEdge))
                .toList());
        response.setAssumptions(List.of(
                "按层级拓扑传播故障，不执行真实 AC/DC 潮流计算",
                "可转供余量按未受影响末端节点额定容量减当前负荷汇总",
                "未供负荷为受影响末端负荷减去可转供余量的正值",
                "当前数据来源为节点独立模拟数据"));
        return response;
    }

    private List<GridNode> activeNodes() {
        return gridNodeMapper.selectList(new LambdaQueryWrapper<GridNode>()
                .eq(GridNode::getStatus, "IN_SERVICE")
                .orderByAsc(GridNode::getSortOrder)
                .orderByAsc(GridNode::getId));
    }

    private Map<Long, GridNode> indexNodes(List<GridNode> nodes) {
        Map<Long, GridNode> byId = new HashMap<>();
        for (GridNode node : nodes) {
            byId.put(node.getId(), node);
        }
        return byId;
    }

    private GridNodeView toNodeView(GridNode node, Map<Long, GridNode> byId) {
        GridNodeView view = new GridNodeView();
        view.setId(node.getId());
        view.setNodeCode(node.getNodeCode());
        view.setNodeName(node.getNodeName());
        view.setNodeType(node.getNodeType());
        view.setParentId(node.getParentId());
        GridNode parent = node.getParentId() == null ? null : byId.get(node.getParentId());
        view.setParentCode(parent != null ? parent.getNodeCode() : null);
        view.setAllocationRatio(node.getAllocationRatio());
        view.setRatedCapacityMw(node.getRatedCapacityMw());
        view.setVoltageLevel(node.getVoltageLevel());
        view.setStatus(node.getStatus());
        view.setTopologyVersion(node.getTopologyVersion());
        view.setSortOrder(node.getSortOrder());
        return view;
    }

    private GridEdgeView toEdgeView(GridEdge edge, Map<Long, GridNode> byId) {
        GridEdgeView view = new GridEdgeView();
        view.setId(edge.getId());
        view.setFromNodeId(edge.getFromNodeId());
        view.setToNodeId(edge.getToNodeId());
        GridNode from = byId.get(edge.getFromNodeId());
        GridNode to = byId.get(edge.getToNodeId());
        view.setFromNodeCode(from != null ? from.getNodeCode() : null);
        view.setToNodeCode(to != null ? to.getNodeCode() : null);
        view.setEdgeType(edge.getEdgeType());
        view.setCapacityMw(edge.getCapacityMw());
        view.setStatus(edge.getStatus());
        view.setTopologyVersion(edge.getTopologyVersion());
        return view;
    }

    private void collectDescendants(Long nodeId,
                                    Map<Long, List<GridNode>> childrenByParent,
                                    Set<Long> collector) {
        if (nodeId == null || !collector.add(nodeId)) {
            return;
        }
        for (GridNode child : childrenByParent.getOrDefault(nodeId, List.of())) {
            collectDescendants(child.getId(), childrenByParent, collector);
        }
    }

    private GridScenarioNodeImpact toScenarioNodeImpact(GridNode node, GridRiskSnapshot risk) {
        GridScenarioNodeImpact impact = new GridScenarioNodeImpact();
        impact.setNodeId(node.getId());
        impact.setNodeCode(node.getNodeCode());
        impact.setNodeName(node.getNodeName());
        impact.setNodeType(node.getNodeType());
        impact.setCurrentLoadMw(risk != null ? risk.getCurrentLoadMw() : null);
        impact.setForecastPeakMw(risk != null ? risk.getForecastPeakMw() : null);
        impact.setImpactLoadMw(risk != null ? risk.getCurrentLoadMw() : null);
        impact.setImpactType("SUPPLY_LOSS");
        return impact;
    }

    private GridScenarioEdgeImpact toScenarioEdgeImpact(GridEdge edge,
                                                        Map<Long, GridNode> nodeById,
                                                        GridEdge failedEdge) {
        GridScenarioEdgeImpact impact = new GridScenarioEdgeImpact();
        impact.setEdgeId(edge.getId());
        GridNode from = nodeById.get(edge.getFromNodeId());
        GridNode to = nodeById.get(edge.getToNodeId());
        impact.setFromNodeCode(from != null ? from.getNodeCode() : null);
        impact.setToNodeCode(to != null ? to.getNodeCode() : null);
        impact.setEdgeType(edge.getEdgeType());
        impact.setCapacityMw(edge.getCapacityMw());
        impact.setImpactType(failedEdge != null && failedEdge.getId().equals(edge.getId())
                ? "FAILED_EDGE" : "DOWNSTREAM_AFFECTED");
        return impact;
    }

    private void calculateMetrics(GridNode node,
                                  double parentCurrentLoad,
                                  double parentForecastPeak,
                                  boolean parentForecastAvailable,
                                  Map<Long, List<GridNode>> childrenByParent,
                                  Map<Long, Double> currentLoads,
                                  Map<Long, ForecastPeak> forecastPeaks,
                                  Map<Long, NodeMetrics> metrics) {
        double ratio = node.getAllocationRatio() != null ? node.getAllocationRatio() : 1.0;
        double currentLoad = currentLoads.getOrDefault(node.getId(), parentCurrentLoad * ratio);
        ForecastPeak directForecast = forecastPeaks.get(node.getId());
        double forecastPeak = directForecast != null && directForecast.available
                ? directForecast.loadMw : parentForecastPeak * ratio;
        boolean forecastAvailable = (directForecast != null && directForecast.available)
                || parentForecastAvailable;
        boolean forecastAdjusted = directForecast != null && directForecast.available
                && directForecast.adjusted;

        List<GridNode> children = childrenByParent.getOrDefault(node.getId(), List.of());
        if (!children.isEmpty()) {
            double childCurrentLoad = 0;
            double childForecastPeak = 0;
            boolean childForecastAvailable = forecastAvailable;
            boolean childForecastAdjusted = forecastAdjusted;
            for (GridNode child : children) {
                calculateMetrics(child, currentLoad, forecastPeak, forecastAvailable,
                        childrenByParent, currentLoads, forecastPeaks, metrics);
                NodeMetrics childMetrics = metrics.get(child.getId());
                childCurrentLoad += childMetrics.currentLoad;
                childForecastPeak += childMetrics.forecastPeak;
                childForecastAvailable |= childMetrics.forecastAvailable;
                childForecastAdjusted |= childMetrics.forecastAdjusted;
            }
            if (childCurrentLoad > 0) {
                currentLoad = childCurrentLoad;
            }
            // 父节点已有独立预测时优先使用父节点预测，不能把各子节点峰值简单相加。
            // 子节点峰值可能发生在不同时间，直接求和会制造虚假的根区域峰值。
            if (childForecastAvailable && (directForecast == null || !directForecast.available)) {
                forecastPeak = childForecastPeak;
                forecastAdjusted = childForecastAdjusted;
            }
            forecastAvailable = childForecastAvailable;
        }

        metrics.put(node.getId(), new NodeMetrics(
                currentLoad, forecastPeak, forecastAvailable, forecastAdjusted));
    }

    private GridRiskSnapshot toRiskSnapshot(GridNode node,
                                            Map<Long, GridNode> byId,
                                            NodeMetrics metrics,
                                            boolean forecastAvailable) {
        double currentLoad = metrics != null ? metrics.currentLoad : 0;
        double forecastPeak = metrics != null ? metrics.forecastPeak : currentLoad;
        double riskLoad = Math.max(currentLoad, forecastPeak);
        Float capacity = node.getRatedCapacityMw();
        String riskLevel = riskLevel(riskLoad, capacity);

        GridRiskSnapshot snapshot = new GridRiskSnapshot();
        snapshot.setNodeId(node.getId());
        snapshot.setNodeCode(node.getNodeCode());
        snapshot.setNodeName(node.getNodeName());
        snapshot.setNodeType(node.getNodeType());
        GridNode parent = node.getParentId() == null ? null : byId.get(node.getParentId());
        snapshot.setParentCode(parent != null ? parent.getNodeCode() : null);
        snapshot.setRatedCapacityMw(capacity);
        snapshot.setCurrentLoadMw((float) round(currentLoad));
        snapshot.setForecastPeakMw((float) round(forecastPeak));
        snapshot.setHeadroomMw(capacity == null ? null : (float) round(capacity - riskLoad));
        snapshot.setRiskLevel(riskLevel);
        snapshot.setRiskBasis(forecastAvailable && forecastPeak >= currentLoad
                ? "FORECAST_PEAK" : "CURRENT_LOAD");
        snapshot.setForecastAvailable(forecastAvailable);
        snapshot.setForecastAdjusted(metrics != null && metrics.forecastAdjusted);
        snapshot.setSimulated(true);
        snapshot.setSource(SOURCE);
        return snapshot;
    }

    private Map<Long, Double> currentLoads(List<GridNode> nodes, double currentRootLoad) {
        Map<Long, Double> result = new HashMap<>();
        result.put(GridTopologyConstants.ROOT_NODE_ID, currentRootLoad);
        if (loadDataMapper == null) {
            return result;
        }
        for (GridNode node : nodes) {
            if (GridTopologyConstants.ROOT_NODE_ID == node.getId()) {
                continue;
            }
            LoadData latest = loadDataMapper.selectOne(new LambdaQueryWrapper<LoadData>()
                    .eq(LoadData::getNodeId, node.getId())
                    .orderByDesc(LoadData::getTime)
                    .last("LIMIT 1"));
            if (latest != null && latest.getLoadMw() != null) {
                result.put(node.getId(), Math.max(0d, latest.getLoadMw().doubleValue()));
            }
        }

        // 秒级演示负荷只更新根区域，节点历史数据仍是小时级快照。
        // 以最新馈线快照保留各节点的独立分布，再按当前根负荷统一缩放，
        // 让负荷突增能够实时传导到馈线和变电站风险。
        Set<Long> parentIds = nodes.stream()
                .map(GridNode::getParentId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        List<GridNode> terminalNodes = nodes.stream()
                .filter(node -> node.getId() != GridTopologyConstants.ROOT_NODE_ID)
                .filter(node -> !parentIds.contains(node.getId()))
                .toList();
        double terminalBaseline = terminalNodes.stream()
                .map(GridNode::getId)
                .map(result::get)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        boolean hasAllTerminalSnapshots = !terminalNodes.isEmpty()
                && terminalNodes.stream().allMatch(node -> result.containsKey(node.getId()));
        if (hasAllTerminalSnapshots && terminalBaseline > 0) {
            double scale = Math.max(0d, currentRootLoad) / terminalBaseline;
            for (GridNode terminalNode : terminalNodes) {
                result.put(terminalNode.getId(), result.get(terminalNode.getId()) * scale);
            }
        }
        return result;
    }

    private double currentRootLoad() {
        RealtimeLoadPoint realtime = realtimeLoadService.getLatest();
        if (realtime != null) {
            return Math.max(0, realtime.getLoadMw());
        }
        LoadData latest = loadDataService.getLatest();
        return latest != null && latest.getLoadMw() != null
                ? Math.max(0, latest.getLoadMw()) : 0;
    }

    private Map<Long, ForecastPeak> latestForecastPeaks(List<GridNode> nodes,
                                                        Map<Long, Double> currentLoads) {
        Map<Long, ForecastPeak> result = new HashMap<>();
        if (loadDataMapper == null) {
            result.put(GridTopologyConstants.ROOT_NODE_ID, latestForecastPeak(
                    GridTopologyConstants.ROOT_NODE_ID));
            return result;
        }
        for (GridNode node : nodes) {
            ForecastPeak peak = latestForecastPeak(node.getId());
            if (peak.available) {
                result.put(node.getId(), normalizeForecastPeak(node.getId(), peak, currentLoads));
            }
        }
        return result;
    }

    private ForecastPeak normalizeForecastPeak(Long nodeId,
                                                ForecastPeak peak,
                                                Map<Long, Double> currentLoads) {
        if (nodeId == null
                || nodeId == GridTopologyConstants.ROOT_NODE_ID
                || !peak.available
                || currentLoads == null) {
            return peak;
        }

        Double currentLoad = currentLoads.get(nodeId);
        if (currentLoad == null || currentLoad <= 0) {
            return peak;
        }

        double maxReasonablePeak = currentLoad * MAX_SIMULATED_FORECAST_RATIO;
        if (peak.loadMw <= maxReasonablePeak) {
            return peak;
        }
        return new ForecastPeak(maxReasonablePeak, true, true);
    }

    private ForecastPeak latestForecastPeak(Long nodeId) {
        PredictionResult latest = predictionResultMapper.selectOne(new LambdaQueryWrapper<PredictionResult>()
                .eq(PredictionResult::getNodeId, nodeId)
                .orderByDesc(PredictionResult::getCreatedAt)
                .last("LIMIT 1"));
        if (latest == null || latest.getCreatedAt() == null) {
            return new ForecastPeak(0, false, false);
        }

        List<PredictionResult> batch = predictionResultMapper.selectList(new LambdaQueryWrapper<PredictionResult>()
                .eq(PredictionResult::getNodeId, nodeId)
                .eq(PredictionResult::getCreatedAt, latest.getCreatedAt())
                .orderByAsc(PredictionResult::getPredictTime));
        double peak = batch.stream()
                .map(PredictionResult::getPredictedLoad)
                .filter(Objects::nonNull)
                .mapToDouble(Float::doubleValue)
                .max()
                .orElse(0);
        return new ForecastPeak(peak, !batch.isEmpty(), false);
    }

    private void markAlertRoots(List<GridRiskSnapshot> snapshots, Map<Long, GridNode> byId) {
        Map<Long, GridRiskSnapshot> riskById = snapshots.stream()
                .collect(java.util.stream.Collectors.toMap(GridRiskSnapshot::getNodeId, snapshot -> snapshot));
        for (GridRiskSnapshot snapshot : snapshots) {
            if (riskRank(snapshot.getRiskLevel()) < 2) {
                snapshot.setRiskReason("NORMAL");
                snapshot.setAlertDeduplicated(false);
                continue;
            }

            GridRiskSnapshot root = snapshot;
            GridNode current = byId.get(snapshot.getNodeId());
            while (current != null && current.getParentId() != null) {
                GridRiskSnapshot parentRisk = riskById.get(current.getParentId());
                if (parentRisk == null || riskRank(parentRisk.getRiskLevel()) < 2) {
                    break;
                }
                root = parentRisk;
                current = byId.get(current.getParentId());
            }
            snapshot.setAlertRootNodeCode(root.getNodeCode());
            snapshot.setAlertDeduplicated(!snapshot.getNodeId().equals(root.getNodeId()));
            snapshot.setRiskReason(buildRiskReason(snapshot));
        }
    }

    private String buildRiskReason(GridRiskSnapshot snapshot) {
        List<String> reasons = new ArrayList<>();
        if ("RED".equals(snapshot.getRiskLevel()) || "ORANGE".equals(snapshot.getRiskLevel())) {
            reasons.add("CAPACITY_OVERLOAD");
        } else {
            reasons.add("CAPACITY_MARGIN_LOW");
        }
        if (snapshot.isForecastAvailable()
                && snapshot.getForecastPeakMw() != null
                && snapshot.getCurrentLoadMw() != null
                && snapshot.getForecastPeakMw() > snapshot.getCurrentLoadMw() * 1.05f) {
            reasons.add("FORECAST_RISE");
        }
        if (snapshot.isForecastAdjusted()) {
            reasons.add("FORECAST_OUTLIER_CAPPED");
        }
        if (snapshot.isAlertDeduplicated()) {
            reasons.add("PARENT_ALERT_DEDUPLICATED");
        }
        return String.join(",", reasons);
    }

    private String riskLevel(double load, Float capacity) {
        if (capacity == null || capacity <= 0) return "UNKNOWN";
        if (load >= capacity * 1.10) return "RED";
        if (load >= capacity) return "ORANGE";
        if (load >= capacity * 0.90) return "YELLOW";
        return "NORMAL";
    }

    private int riskRank(String riskLevel) {
        return switch (riskLevel) {
            case "RED" -> 4;
            case "ORANGE" -> 3;
            case "YELLOW" -> 2;
            case "NORMAL" -> 1;
            default -> 0;
        };
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record NodeMetrics(double currentLoad,
                               double forecastPeak,
                               boolean forecastAvailable,
                               boolean forecastAdjusted) {
    }

    private record ForecastPeak(double loadMw, boolean available, boolean adjusted) {
    }
}
