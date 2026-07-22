package com.powerload.alert;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.agent.AgentMessage;
import com.powerload.agent.LlmClient;
import com.powerload.dto.response.AlertJudgementResult;
import com.powerload.dto.response.ForecastResponse;
import com.powerload.dto.response.GridRiskSnapshot;
import com.powerload.dto.response.GridResponsibility;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.AlertAdvice;
import com.powerload.entity.AlertEvent;
import com.powerload.entity.AlertTicket;
import com.powerload.mapper.AlertAdviceMapper;
import com.powerload.mapper.AlertTicketMapper;
import com.powerload.service.GridTopologyService;
import com.powerload.service.PredictService;
import com.powerload.service.RealtimeLoadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能研判服务 — 纯规则引擎，不调用 LLM。
 * <p>
 * 判断逻辑全部使用确定性 Java 条件，零 LLM 成本。
 * 结果存入 alert_advice 表（复用），同一 alertId 幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertJudgementService {

    private final AlertAdviceMapper alertAdviceMapper;
    private final AlertTicketMapper alertTicketMapper;
    private final RealtimeLoadService realtimeLoadService;
    private final PredictService predictService;
    private final GridTopologyService gridTopologyService;
    private final LlmClient llmClient;

    private static final String SOURCE_RULE = "RULE_BASED_AGENT";
    private static final String SOURCE_LLM = "LLM_AGENT";
    private static final String SOURCE_FALLBACK = "RULE_BASED_FALLBACK";

    /** 计算趋势方向：取最近 {@code minutes} 分钟内的实时点 */
    public String detectTrend(int minutes) {
        List<RealtimeLoadPoint> pts = realtimeLoadService.getRecent(minutes);
        if (pts == null || pts.size() < 2) return "UNKNOWN";
        float first = pts.get(0).getLoadMw();
        float last = pts.get(pts.size() - 1).getLoadMw();
        if (first == 0) return "UNKNOWN";
        double ratio = (last - first) / first;
        if (ratio > 0.02) return "RISING";
        if (ratio < -0.02) return "FALLING";
        return "STABLE";
    }

    /** 获取最新预测峰值 */
    private float[] getForecastPeak() {
        try {
            ForecastResponse f = predictService.forecast();
            if (f == null || f.getPredictions() == null || f.getPredictions().isEmpty()) return new float[]{0, 0};
            List<Double> preds = f.getPredictions();
            float peak = Float.MIN_VALUE;
            int peakIdx = -1;
            for (int i = 0; i < preds.size(); i++) {
                float v = preds.get(i).floatValue();
                if (v > peak) { peak = v; peakIdx = i; }
            }
            return new float[]{peak, peakIdx};
        } catch (Exception e) {
            return new float[]{0, -1};
        }
    }

    private float[] getTopologyForecastPeak(AlertEvent alert) {
        if (gridTopologyService == null || alert.getNodeId() == null) {
            return getForecastPeak();
        }
        try {
            GridRiskSnapshot snapshot = findTopologySnapshot(alert);
            if (snapshot != null && snapshot.getForecastPeakMw() != null) {
                return new float[]{snapshot.getForecastPeakMw(), 0};
            }
        } catch (Exception e) {
            log.debug("拓扑告警预测峰值读取失败: nodeId={}", alert.getNodeId(), e);
        }
        return getForecastPeak();
    }

    private GridRiskSnapshot findTopologySnapshot(AlertEvent alert) {
        if (gridTopologyService == null || alert == null || alert.getNodeId() == null) {
            return null;
        }
        try {
            return gridTopologyService.getRiskSnapshot().stream()
                    .filter(item -> alert.getNodeId().equals(item.getNodeId()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.debug("拓扑风险证据读取失败: nodeId={}, reason={}", alert.getNodeId(), e.getMessage());
            return null;
        }
    }

    /** 是否有同告警对应的未关闭工单 */
    private boolean hasExistingTicketForAlert(Long alertId) {
        Long count = alertTicketMapper.selectCount(
                new LambdaQueryWrapper<AlertTicket>()
                        .eq(AlertTicket::getAlertId, alertId)
                        .notIn(AlertTicket::getStatus, "CLOSED", "CANCELLED"));
        return count != null && count > 0;
    }

    /** 是否有同级别（或更高）未关闭工单 */
    private boolean hasOpenSimilarTicket(String level) {
        // 同级别或更高级别（RED > ORANGE > YELLOW）的未关闭工单
        List<String> levels;
        if ("RED".equals(level)) {
            levels = List.of("RED");
        } else if ("ORANGE".equals(level)) {
            levels = List.of("RED", "ORANGE");
        } else {
            levels = List.of("RED", "ORANGE", "YELLOW");
        }
        // 通过 alert_event 表关联查找
        Long count = alertTicketMapper.selectCount(
                new LambdaQueryWrapper<AlertTicket>()
                        .notIn(AlertTicket::getStatus, "CLOSED", "CANCELLED")
                        .inSql(AlertTicket::getAlertId,
                                "SELECT id FROM alert_event WHERE level IN ('" + String.join("','", levels) + "')"));
        return count != null && count > 0;
    }

    /**
     * 核心研判逻辑 — 输入 AlertEvent，输出 AlertJudgementResult
     */
    public AlertJudgementResult judge(AlertEvent alert) {
        return judge(alert, true);
    }

    public AlertJudgementResult judgeRuleBased(AlertEvent alert) {
        return judge(alert, false);
    }

    private AlertJudgementResult judge(AlertEvent alert, boolean useLlm) {
        Float currentLoad = alert.getCurrentValue();
        Float threshold = alert.getThresholdValue();
        String level = alert.getLevel();
        Long alertId = alert.getId();

        // 1. 聚合上下文
        String trend = detectTrend(5); // 5 分钟窗口
        float[] peakInfo = "TOPOLOGY_RISK".equals(alert.getType())
                ? getTopologyForecastPeak(alert)
                : getForecastPeak();
        float forecastPeak = peakInfo[0];
        float forecastPeakIdx = peakInfo[1];
        boolean topologyRisk = "TOPOLOGY_RISK".equals(alert.getType());
        GridRiskSnapshot topologySnapshot = topologyRisk ? findTopologySnapshot(alert) : null;

        // 预测峰值明显低于当前负荷 → 预测数据可能过期，标记为不可靠
        boolean forecastReliable = true;
        if (forecastPeak > 0 && currentLoad != null && forecastPeak < currentLoad * 0.9f) {
            forecastReliable = false;
            log.debug("预测峰值 {} MW 低于当前负荷 {} MW，预测数据可能过期", forecastPeak, currentLoad);
        }

        boolean hasTicket = hasExistingTicketForAlert(alertId);
        boolean hasSimilar = hasOpenSimilarTicket(level);

        // 2. 规则判断
        boolean shouldCreate = false;
        boolean autoCreate = false;
        String priority = "NORMAL";
        StringBuilder reason = new StringBuilder();

        if ("RED".equals(level)) {
            if (!hasTicket && !hasSimilar) {
                shouldCreate = true;
                autoCreate = false;
                priority = "URGENT";
                if (topologyRisk) {
                    appendTopologyRiskReason(reason, currentLoad, threshold, forecastPeak);
                } else {
                    reason.append("红色告警，负荷 ")
                            .append(String.format("%.1f", currentLoad))
                            .append(" MW ")
                            .append("超过阈值 ")
                            .append(String.format("%.0f", threshold))
                            .append(" MW");
                }
                reason.append("，当前无对应工单且无同类未关闭工单，建议立即创建工单处置。");
            } else if (hasTicket) {
                shouldCreate = false;
                reason.append("该告警已存在工单，无需重复创建。");
            } else {
                shouldCreate = false;
                reason.append("存在同类未关闭工单，建议合并处理，避免重复建单。");
            }
        } else if ("ORANGE".equals(level)) {
            boolean risky = forecastReliable && forecastPeak > 0 && forecastPeak >= threshold * 1.1f;
            boolean rising = "RISING".equals(trend);
            if (risky || rising) {
                shouldCreate = true;
                priority = "HIGH";
                reason.append("橙色告警，");
                if (risky) reason.append("未来预测峰值 ").append(String.format("%.0f", forecastPeak)).append(" MW 可能达到红色区间，");
                if (rising) reason.append("当前负荷处于上升趋势，");
                if (!forecastReliable && forecastPeak > 0) reason.append("（注：预测数据可能过期，仅供参考）");
                reason.append("建议创建工单跟踪。");
            } else {
                reason.append("橙色告警，");
                if (!forecastReliable) reason.append("预测数据可能过期，");
                reason.append("预测未达红色且趋势平稳，建议持续观察，暂不建单。");
            }
        } else { // YELLOW
            boolean risky = forecastReliable && forecastPeak > 0 && forecastPeak >= threshold * 1.1f;
            if (risky) {
                shouldCreate = true;
                priority = forecastPeak >= threshold * 1.2f ? "HIGH" : "NORMAL";
                reason.append("黄色告警，未来预测峰值 ").append(String.format("%.0f", forecastPeak))
                      .append(" MW 可能升级，建议创建预警工单跟踪。");
            } else {
                reason.append("黄色告警，");
                if (!forecastReliable && forecastPeak > 0) reason.append("预测数据可能过期，");
                reason.append("暂未达到建议建单条件，建议观察。");
            }
        }

        // 3. 生成建议文案
        String dispAdvice = buildDispatcherAdvice(
                level, currentLoad, threshold, shouldCreate, autoCreate, trend, forecastPeak, topologyRisk);
        String operAdvice = buildOperatorAdvice(
                level, currentLoad, threshold, shouldCreate, autoCreate, trend, topologyRisk);
        GridResponsibility responsibility = gridTopologyService.resolveResponsibility(alert.getNodeId());
        DraftFields draft = buildDraftFields(alert, currentLoad, threshold, forecastPeak,
                trend, reason.toString(), responsibility, topologySnapshot);

        // 4. 构建结果
        AlertJudgementResult result = AlertJudgementResult.builder()
                .alertId(alertId)
                .nodeId(alert.getNodeId())
                .nodeCode(topologySnapshot != null ? topologySnapshot.getNodeCode() : null)
                .nodeName(topologySnapshot != null ? topologySnapshot.getNodeName() : null)
                .substationCode(responsibility != null ? responsibility.getSubstationCode() : null)
                .substationName(responsibility != null ? responsibility.getSubstationName() : null)
                .level(level)
                .currentLoad(currentLoad)
                .thresholdValue(threshold)
                .impactLoadMw(alert.getImpactLoadMw())
                .headroomMw(topologySnapshot != null ? topologySnapshot.getHeadroomMw() : null)
                .riskBasis(topologySnapshot != null ? topologySnapshot.getRiskBasis() : null)
                .riskReason(topologySnapshot != null ? topologySnapshot.getRiskReason() : null)
                .alertRootNodeCode(topologySnapshot != null ? topologySnapshot.getAlertRootNodeCode() : null)
                .trendDirection(trend)
                .forecastPeakLoad(forecastPeak > 0 ? forecastPeak : null)
                .forecastPeakTime(forecastPeak > 0 ? LocalDateTime.now().plusHours((long) forecastPeakIdx) : null)
                .hasExistingTicket(hasTicket)
                .hasOpenSimilarTicket(hasSimilar)
                .shouldCreateTicket(shouldCreate)
                .autoCreateTicket(autoCreate)
                .recommendedPriority(priority)
                .dispatcherAdvice(dispAdvice)
                .operatorAdvice(operAdvice)
                .decisionReason(reason.toString())
                .source(SOURCE_RULE)
                .ticketTitle(draft.title())
                .ticketSummary(draft.summary())
                .rootCauseHints(draft.rootCauses())
                .impactScope(draft.impactScope())
                .recommendedAssigneeUserId(responsibility != null
                        ? responsibility.getAssigneeUserId() : null)
                .recommendedAssigneeName(responsibility != null
                        ? responsibility.getAssigneeName() : null)
                .routingTarget(responsibility != null && !responsibility.isDispatchCenter()
                        ? "SUBSTATION_OPERATOR" : "DISPATCH_CENTER")
                .routingReason(responsibility != null
                        ? responsibility.getRouteReason() : "NO_NODE")
                .createdAt(LocalDateTime.now())
                .build();

        if (useLlm) {
            result = enrichWithLlm(result);
        }

        // 5. 存入 alert_advice（幂等：同一 alertId 已有成功记录则跳过）
        persistJudgement(alertId, result);

        log.info("智能研判完成: alertId={}, level={}, shouldCreate={}, autoCreate={}, priority={}",
                alertId, level, shouldCreate, autoCreate, priority);
        return result;
    }

    /** 查询已有研判（用于 GET 接口幂等） */
    public AlertJudgementResult getExistingJudgement(Long alertId) {
        // 从 alert_advice 读取 RULE_BASED_AGENT 记录
        var wrapper = new LambdaQueryWrapper<AlertAdvice>()
                .eq(AlertAdvice::getAlertId, alertId)
                .eq(AlertAdvice::getAudienceRole, "JUDGEMENT")
                .eq(AlertAdvice::getStatus, "SUCCESS")
                .last("LIMIT 1");
        AlertAdvice adv = alertAdviceMapper.selectOne(wrapper);
        if (adv == null || adv.getAnalysis() == null) return null;
        // 由于我们复用了 alert_advice，从 analysis/evidence 字段反序列化
        try {
            return rebuildFromAdvice(alertId, adv);
        } catch (Exception e) {
            log.debug("从已有 advice 重建研判失败", e);
            return null;
        }
    }

    /** 幂等写入 alert_advice */
    private void persistJudgement(Long alertId, AlertJudgementResult result) {
        try {
            AlertAdvice adv = new AlertAdvice();
            adv.setAlertId(alertId);
            adv.setAudienceRole("JUDGEMENT");
            adv.setStatus("SUCCESS");
            adv.setAnalysis(serializeJudgement(result));
            adv.setEvidence("{}");
            adv.setModelName(result.getSource());
            adv.setGeneratedAt(LocalDateTime.now());
            adv.setCreatedAt(LocalDateTime.now());

            // 幂等：如果已有就更新
            var existing = alertAdviceMapper.selectList(
                    new LambdaQueryWrapper<AlertAdvice>()
                            .eq(AlertAdvice::getAlertId, alertId)
                            .eq(AlertAdvice::getAudienceRole, "JUDGEMENT"));
            if (existing != null && !existing.isEmpty()) {
                adv.setId(existing.get(0).getId());
                alertAdviceMapper.updateById(adv);
            } else {
                alertAdviceMapper.insert(adv);
            }
        } catch (Exception e) {
            log.warn("研判结果持久化失败: alertId={}", alertId, e);
        }
    }

    private String serializeJudgement(AlertJudgementResult r) {
        return cn.hutool.json.JSONUtil.toJsonStr(r);
    }

    private AlertJudgementResult rebuildFromAdvice(Long alertId, AlertAdvice adv) {
        try {
            return cn.hutool.json.JSONUtil.toBean(adv.getAnalysis(), AlertJudgementResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private DraftFields buildDraftFields(AlertEvent alert,
                                         Float currentLoad,
                                         Float threshold,
                                         float forecastPeak,
                                         String trend,
                                         String reason,
                                         GridResponsibility responsibility,
                                         GridRiskSnapshot topologySnapshot) {
        String nodeName = responsibility != null && responsibility.getSourceNodeName() != null
                ? responsibility.getSourceNodeName() : "根区域";
        String substationName = responsibility != null && responsibility.getSubstationName() != null
                ? responsibility.getSubstationName() : "调度中心";
        String title = ("TOPOLOGY_RISK".equals(alert.getType()) ? "拓扑风险" : "负荷告警")
                + " · " + nodeName;
        StringBuilder summary = new StringBuilder()
                .append(title)
                .append("，当前负荷 ")
                .append(formatValue(currentLoad))
                .append(" MW，阈值/容量 ")
                .append(formatValue(threshold))
                .append(" MW。");
        if (forecastPeak > 0) {
            summary.append("预测峰值 ").append(formatValue(forecastPeak)).append(" MW。");
        }
        summary.append("趋势：").append(trend).append("。");
        summary.append("责任域：").append(substationName).append("。");
        summary.append("建议调度员确认后提交工单。");

        List<String> roots = new java.util.ArrayList<>();
        roots.add("规则判定：" + reason);
        if ("RISING".equals(trend)) {
            roots.add("负荷近期呈上升趋势，需核对新增负荷或运行方式变化");
        }
        if ("TOPOLOGY_RISK".equals(alert.getType())) {
            roots.add("优先核对责任变电站下游馈线负荷分布和容量余量");
            if (topologySnapshot != null && topologySnapshot.getRiskReason() != null) {
                roots.add("拓扑计算依据：" + topologySnapshot.getRiskReason());
            }
        } else {
            roots.add("核对实时采集、告警阈值及相关设备运行状态");
        }

        List<String> impact = new java.util.ArrayList<>();
        impact.add(nodeName);
        if (responsibility != null && responsibility.getSubstationCode() != null) {
            impact.add(responsibility.getSubstationName() + "责任域");
        }
        if (alert.getImpactLoadMw() != null) {
            impact.add("预计影响负荷 " + formatValue(alert.getImpactLoadMw()) + " MW");
        }
        if (topologySnapshot != null && topologySnapshot.getAlertRootNodeCode() != null) {
            impact.add("根告警节点 " + topologySnapshot.getAlertRootNodeCode());
        }
        return new DraftFields(title, truncate(summary.toString(), 480), roots, impact);
    }

    private String formatValue(Float value) {
        return value == null ? "--" : String.format("%.1f", value);
    }

    private AlertJudgementResult enrichWithLlm(AlertJudgementResult base) {
        try {
            String content = callJudgementLlm(base);
            JSONObject json = JSONUtil.parseObj(cleanJson(content));
            String decisionReason = firstNonBlank(json.getStr("decisionReason"), json.getStr("reason"));
            String dispatcherAdvice = firstNonBlank(json.getStr("dispatcherAdvice"), json.getStr("dispatcher"));
            String operatorAdvice = firstNonBlank(json.getStr("operatorAdvice"), json.getStr("operator"));
            String ticketTitle = json.getStr("ticketTitle");
            String ticketSummary = json.getStr("ticketSummary");
            List<String> rootCauseHints = extractStringList(json, "rootCauseHints");
            List<String> impactScope = extractStringList(json, "impactScope");

            if (decisionReason != null) base.setDecisionReason(truncate(decisionReason, 800));
            if (dispatcherAdvice != null) base.setDispatcherAdvice(truncate(dispatcherAdvice, 800));
            if (operatorAdvice != null) base.setOperatorAdvice(truncate(operatorAdvice, 800));
            if (ticketTitle != null && !ticketTitle.isBlank()) {
                base.setTicketTitle(truncate(ticketTitle, 120));
            }
            if (ticketSummary != null && !ticketSummary.isBlank()) {
                base.setTicketSummary(truncate(ticketSummary, 480));
            }
            if (!rootCauseHints.isEmpty()) base.setRootCauseHints(rootCauseHints);
            if (!impactScope.isEmpty()) base.setImpactScope(impactScope);
            base.setSource(SOURCE_LLM);
        } catch (Exception e) {
            log.warn("LLM alert judgement failed, using rule fallback: alertId={}, reason={}",
                    base.getAlertId(), e.getMessage());
            base.setSource(SOURCE_FALLBACK);
        }
        return base;
    }

    private List<String> extractStringList(JSONObject json, String key) {
        Object raw = json.get(key);
        if (raw instanceof JSONArray array) {
            List<String> result = new java.util.ArrayList<>();
            for (Object item : array) {
                if (item != null && !item.toString().isBlank()) {
                    result.add(truncate(item.toString(), 120));
                }
            }
            return result.stream().limit(6).toList();
        }
        String text = json.getStr(key);
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            return extractStringList(JSONUtil.parseObj("{\"items\":" + text + "}"), "items");
        } catch (Exception ignored) {
            return List.of(truncate(text, 120));
        }
    }

    private String callJudgementLlm(AlertJudgementResult base) throws Exception {
        AgentMessage response = llmClient.chat(List.of(
                AgentMessage.system("你是电力调度告警研判专家。只输出严格 JSON，不要输出 Markdown。"),
                AgentMessage.user(buildJudgementPrompt(base))
        ), null);
        String content = response == null ? null : response.getContent();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("LLM returned empty judgement");
        }
        return content;
    }

    private String buildJudgementPrompt(AlertJudgementResult base) {
        return """
                请基于以下规则研判结果，生成面向用户的 AI 智能研判文案。
                必须遵守：
                - 不要改变 shouldCreateTicket、autoCreateTicket、recommendedPriority 等系统决策字段。
                - 红色告警只能建议调度员立即确认待确认工单草稿，不要声称系统已经提交正式工单。
                - 不要编造未提供的设备故障、线路跳闸、人工处置结果。
                - 返回 JSON：{\"decisionReason\":\"...\",\"dispatcherAdvice\":\"...\",\"operatorAdvice\":\"...\",
                  \"ticketTitle\":\"...\",\"ticketSummary\":\"...\",\"rootCauseHints\":[\"...\"],\"impactScope\":[\"...\"]}
                - ticketTitle/ticketSummary 用于生成待确认工单草稿，不能声称工单已经提交。
                - rootCauseHints 只能写基于证据的候选原因，不能编造设备故障。
                - impactScope 只能引用输入证据中的节点、责任域和影响负荷。

                规则研判结果：
                %s
                """.formatted(JSONUtil.toJsonStr(base));
    }

    private String cleanJson(String raw) {
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private record DraftFields(String title,
                               String summary,
                               List<String> rootCauses,
                               List<String> impactScope) {
    }

    /* ─── 建议文案模板 ─── */

    private void appendTopologyRiskReason(StringBuilder reason,
                                          float currentLoad,
                                          float threshold,
                                          float forecastPeak) {
        if (forecastPeak > 0 && forecastPeak >= threshold && forecastPeak > currentLoad) {
            reason.append("拓扑红色风险，预测峰值 ")
                    .append(String.format("%.1f", forecastPeak))
                    .append(" MW 超过节点容量 ")
                    .append(String.format("%.0f", threshold))
                    .append(" MW");
            return;
        }
        if (currentLoad >= threshold) {
            reason.append("拓扑红色风险，当前负荷 ")
                    .append(String.format("%.1f", currentLoad))
                    .append(" MW 超过节点容量 ")
                    .append(String.format("%.0f", threshold))
                    .append(" MW");
            return;
        }
        reason.append("拓扑红色风险，但当前负荷 ")
                .append(String.format("%.1f", currentLoad))
                .append(" MW、预测峰值 ")
                .append(forecastPeak > 0 ? String.format("%.1f", forecastPeak) : "--")
                .append(" MW 均未超过节点容量 ")
                .append(String.format("%.0f", threshold))
                .append(" MW，建议核对告警快照和数据时效");
    }

    private String buildDispatcherAdvice(String level, float load, float threshold,
                                          boolean should, boolean auto, String trend, float peak,
                                          boolean topologyRisk) {
        StringBuilder sb = new StringBuilder();
        if (topologyRisk) {
            sb.append("节点当前负荷 ")
                    .append(String.format("%.1f", load))
                    .append(" MW，节点容量 ")
                    .append(String.format("%.0f", threshold))
                    .append(" MW。");
            if (peak > threshold && peak > load) {
                sb.append("预测峰值约")
                        .append(String.format("%.0f", peak))
                        .append(" MW，存在预测越限风险。");
            } else if (load >= threshold) {
                sb.append("当前负荷已超过节点容量，需要核查下游负荷和转供余量。");
            } else {
                sb.append("当前负荷和预测峰值均未超过节点容量，建议先核对告警快照和数据时效。");
            }
        } else {
            sb.append("负荷").append(String.format("%.1f", load)).append(" MW，超阈值")
                    .append(String.format("%.0f", threshold)).append(" MW。");
        }
        if ("RISING".equals(trend)) sb.append("当前负荷处于上升趋势，需密切关注。");
        if ("FALLING".equals(trend)) sb.append("当前负荷正在回落，但仍需监控。");
        if (peak > 0 && !topologyRisk) {
            sb.append("预测峰值约").append(String.format("%.0f", peak)).append(" MW。");
        }
        if (auto) sb.append("系统已生成待确认工单草稿，请调度员核对后提交正式工单。");
        else if (should) sb.append("建议创建工单跟踪处理。");
        else sb.append("暂不建议建单，建议持续观察负荷变化。");
        sb.append("（规则型 Agent 自动研判，仅供人工决策参考）");
        return sb.toString();
    }

    private String buildOperatorAdvice(String level, float load, float threshold,
                                        boolean should, boolean auto, String trend,
                                        boolean topologyRisk) {
        StringBuilder sb = new StringBuilder();
        if (auto) {
            sb.append("红色紧急告警已生成待确认工单草稿，请立即核查数据源和告警规则配置。");
        } else if (should) {
            sb.append(topologyRisk ? "拓扑节点风险，建议核查下游馈线负荷和容量余量，"
                    : "橙色/黄色告警，建议关注负荷趋势，");
            if ("RISING".equals(trend)) sb.append("如持续上升需准备升级响应。");
            else sb.append("核实数据采集和设备状态。");
        } else {
            sb.append("告警级别较低，建议检查数据采集通道和阈值配置是否正常。");
        }
        sb.append("（规则型 Agent 自动研判，仅供人工决策参考）");
        return sb.toString();
    }
}
