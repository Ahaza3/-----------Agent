package com.powerload.alert;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.agent.AgentMessage;
import com.powerload.agent.LlmClient;
import com.powerload.dto.response.AlertJudgementResult;
import com.powerload.dto.response.ForecastResponse;
import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.AlertAdvice;
import com.powerload.entity.AlertEvent;
import com.powerload.entity.AlertTicket;
import com.powerload.mapper.AlertAdviceMapper;
import com.powerload.mapper.AlertTicketMapper;
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
        float[] peakInfo = getForecastPeak();
        float forecastPeak = peakInfo[0];
        float forecastPeakIdx = peakInfo[1];

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
                reason.append("红色告警，负荷 ").append(String.format("%.1f", currentLoad))
                      .append(" MW 超过阈值 ").append(String.format("%.0f", threshold))
                      .append(" MW，当前无对应工单且无同类未关闭工单，建议立即创建工单处置。");
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
        String dispAdvice = buildDispatcherAdvice(level, currentLoad, threshold, shouldCreate, autoCreate, trend, forecastPeak);
        String operAdvice = buildOperatorAdvice(level, currentLoad, threshold, shouldCreate, autoCreate, trend);

        // 4. 构建结果
        AlertJudgementResult result = AlertJudgementResult.builder()
                .alertId(alertId)
                .level(level)
                .currentLoad(currentLoad)
                .thresholdValue(threshold)
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

    private AlertJudgementResult enrichWithLlm(AlertJudgementResult base) {
        try {
            String content = callJudgementLlm(base);
            JSONObject json = JSONUtil.parseObj(cleanJson(content));
            String decisionReason = firstNonBlank(json.getStr("decisionReason"), json.getStr("reason"));
            String dispatcherAdvice = firstNonBlank(json.getStr("dispatcherAdvice"), json.getStr("dispatcher"));
            String operatorAdvice = firstNonBlank(json.getStr("operatorAdvice"), json.getStr("operator"));

            if (decisionReason != null) base.setDecisionReason(truncate(decisionReason, 800));
            if (dispatcherAdvice != null) base.setDispatcherAdvice(truncate(dispatcherAdvice, 800));
            if (operatorAdvice != null) base.setOperatorAdvice(truncate(operatorAdvice, 800));
            base.setSource(SOURCE_LLM);
        } catch (Exception e) {
            log.warn("LLM alert judgement failed, using rule fallback: alertId={}, reason={}",
                    base.getAlertId(), e.getMessage());
            base.setSource(SOURCE_FALLBACK);
        }
        return base;
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
                - 红色告警只能建议调度员立即确认并建单，不要声称系统已经自动建单。
                - 不要编造未提供的设备故障、线路跳闸、人工处置结果。
                - 返回 JSON：{\"decisionReason\":\"...\",\"dispatcherAdvice\":\"...\",\"operatorAdvice\":\"...\"}

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

    /* ─── 建议文案模板 ─── */

    private String buildDispatcherAdvice(String level, float load, float threshold,
                                          boolean should, boolean auto, String trend, float peak) {
        StringBuilder sb = new StringBuilder();
        sb.append("负荷").append(String.format("%.1f", load)).append(" MW，超阈值")
          .append(String.format("%.0f", threshold)).append(" MW。");
        if ("RISING".equals(trend)) sb.append("当前负荷处于上升趋势，需密切关注。");
        if ("FALLING".equals(trend)) sb.append("当前负荷正在回落，但仍需监控。");
        if (peak > 0) sb.append("预测峰值约").append(String.format("%.0f", peak)).append(" MW。");
        if (auto) sb.append("系统已自动创建紧急工单，请立即指派运维人员处置。");
        else if (should) sb.append("建议创建工单跟踪处理。");
        else sb.append("暂不建议建单，建议持续观察负荷变化。");
        sb.append("（规则型 Agent 自动研判，仅供人工决策参考）");
        return sb.toString();
    }

    private String buildOperatorAdvice(String level, float load, float threshold,
                                        boolean should, boolean auto, String trend) {
        StringBuilder sb = new StringBuilder();
        if (auto) {
            sb.append("红色紧急告警已自动创建工单，请立即核查数据源和告警规则配置。");
        } else if (should) {
            sb.append("橙色/黄色告警，建议关注负荷趋势，");
            if ("RISING".equals(trend)) sb.append("如持续上升需准备升级响应。");
            else sb.append("核实数据采集和设备状态。");
        } else {
            sb.append("告警级别较低，建议检查数据采集通道和阈值配置是否正常。");
        }
        sb.append("（规则型 Agent 自动研判，仅供人工决策参考）");
        return sb.toString();
    }
}
