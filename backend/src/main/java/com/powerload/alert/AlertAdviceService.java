package com.powerload.alert;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.agent.AgentMessage;
import com.powerload.agent.LlmClient;
import com.powerload.agent.LlmException;
import com.powerload.entity.AlertAdvice;
import com.powerload.entity.AlertEvent;
import com.powerload.mapper.AlertAdviceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class AlertAdviceService {

    private final AlertAdviceMapper alertAdviceMapper;
    private final LlmClient llmClient;
    private final ExecutorService executor;

    /** LLM 输出最大长度（字符） */
    private static final int MAX_OUTPUT_LENGTH = 3000;

    public AlertAdviceService(AlertAdviceMapper alertAdviceMapper, LlmClient llmClient) {
        this.alertAdviceMapper = alertAdviceMapper;
        this.llmClient = llmClient;
        this.executor = new ThreadPoolExecutor(1, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(20),
                r -> new Thread(r, "alert-advice"),
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    @Async
    @EventListener
    public void onAlertCreated(AlertCreatedEvent event) {
        AlertEvent alert = event.getAlert();
        executor.submit(() -> generateAdvice(alert));
    }

    void generateAdvice(AlertEvent alert) {
        Long alertId = alert.getId();
        var existing = alertAdviceMapper.selectList(
                new LambdaQueryWrapper<AlertAdvice>().eq(AlertAdvice::getAlertId, alertId));
        if (!existing.isEmpty() && existing.stream().anyMatch(a -> "SUCCESS".equals(a.getStatus()))) {
            log.debug("告警 {} 已有成功建议，跳过", alertId);
            return;
        }

        // 预插入 PENDING
        for (String role : List.of("DISPATCHER", "OPERATOR")) {
            try {
                AlertAdvice adv = new AlertAdvice();
                adv.setAlertId(alertId);
                adv.setAudienceRole(role);
                adv.setStatus("PENDING");
                adv.setCreatedAt(LocalDateTime.now());
                alertAdviceMapper.insert(adv);
            } catch (Exception ignored) { /* 幂等 */ }
        }

        Map<String, Object> evidence = buildEvidence(alert);
        String evidenceJson = JSONUtil.toJsonStr(evidence);

        try {
            // LLM 调用 — 超时控制
            String llmResult = callLlmWithTimeout(alert, evidenceJson);
            if (llmResult == null || llmResult.isBlank()) {
                throw new RuntimeException("LLM 返回空内容");
            }

            // 解析 JSON
            String cleaned = llmResult.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```[a-z]*\n?", "").replaceAll("```$", "").trim();
            }
            JSONObject json = JSONUtil.parseObj(cleaned);
            if (json.isEmpty()) throw new RuntimeException("JSON 解析为空");

            // 提取两个角色建议
            JSONObject dispJson = json.getJSONObject("dispatcher");
            JSONObject operJson = json.getJSONObject("operator");

            if (dispJson == null || operJson == null) {
                // 尝试驼峰/下划线变体
                dispJson = json.getJSONObject("disp");
                operJson = json.getJSONObject("oper");
            }

            if (dispJson == null && operJson == null) {
                throw new RuntimeException("JSON 缺少 dispatcher/operator 字段: " + trunc(cleaned));
            }

            String modelName = json.getStr("model", "LLM");

            // 写入 DISPATCHER
            updateToSuccess(alertId, "DISPATCHER",
                    dispJson != null ? dispJson.getStr("analysis", "") : "分析不可用",
                    extractActions(dispJson),
                    evidenceJson, modelName);

            // 写入 OPERATOR
            updateToSuccess(alertId, "OPERATOR",
                    operJson != null ? operJson.getStr("analysis", "") : "分析不可用",
                    extractActions(operJson),
                    evidenceJson, modelName);

            log.info("AI 建议生成成功: alertId={}", alertId);

        } catch (Exception e) {
            log.warn("AI 建议生成失败，使用降级: alertId={}, reason={}", alertId, e.getMessage());
            useFallback(alertId, evidence, e.getMessage());
        }
    }

    private String callLlmWithTimeout(AlertEvent alert, String evidenceJson) throws Exception {
        String prompt = buildAdvicePrompt(evidenceJson);
        List<AgentMessage> messages = List.of(
                AgentMessage.system("你是电力系统告警分析专家。只生成指定格式的JSON，不要输出其他内容。"),
                AgentMessage.user(prompt));

        // 在当前线程直接调用 LLM（generateAdvice 已在线程池中，无需再嵌套）
        AgentMessage resp = llmClient.chat(messages, null);
        String content = resp.getContent();
        if (content == null || content.isBlank()) {
            throw new RuntimeException("LLM 返回空内容");
        }
        return content.length() > MAX_OUTPUT_LENGTH
                ? content.substring(0, MAX_OUTPUT_LENGTH) : content;
    }

    private String extractActions(JSONObject json) {
        if (json == null) return "[]";
        Object acts = json.get("actions");
        if (acts instanceof JSONArray arr) return arr.toString();
        if (acts instanceof List<?> list) return JSONUtil.toJsonStr(list);
        String s = json.getStr("actions", "");
        if (!s.isBlank()) {
            // 尝试解析为 JSON 数组
            try {
                JSONArray arr = JSONUtil.parseArray(s);
                if (arr != null) return arr.toString();
            } catch (Exception ignored) {}
            return "[\"" + s.replace("\"", "\\\"") + "\"]";
        }
        return "[]";
    }

    private void updateToSuccess(Long alertId, String role, String analysis,
                                  String actions, String evidence, String modelName) {
        var wrapper = new LambdaQueryWrapper<AlertAdvice>()
                .eq(AlertAdvice::getAlertId, alertId)
                .eq(AlertAdvice::getAudienceRole, role);
        AlertAdvice adv = alertAdviceMapper.selectOne(wrapper);
        if (adv == null) return;
        adv.setStatus("SUCCESS");
        adv.setAnalysis(analysis);
        adv.setActions(actions);
        adv.setEvidence(evidence);
        adv.setModelName(modelName);
        adv.setGeneratedAt(LocalDateTime.now());
        adv.setErrorMessage(null);
        alertAdviceMapper.updateById(adv);
    }

    void useFallback(Long alertId, Map<String, Object> evidence, String errorMsg) {
        for (String role : List.of("DISPATCHER", "OPERATOR")) {
            try {
                var wrapper = new LambdaQueryWrapper<AlertAdvice>()
                        .eq(AlertAdvice::getAlertId, alertId)
                        .eq(AlertAdvice::getAudienceRole, role);
                AlertAdvice advice = alertAdviceMapper.selectOne(wrapper);
                if (advice == null) continue;

                advice.setStatus("FALLBACK");
                advice.setErrorMessage(trunc(errorMsg));
                advice.setGeneratedAt(LocalDateTime.now());

                if ("DISPATCHER".equals(role)) {
                    advice.setAnalysis("负荷" + evidence.get("level") + "告警：当前 "
                            + evidence.get("currentLoad") + " MW，超阈值 "
                            + evidence.get("threshold") + " MW 约 "
                            + evidence.get("overRatio") + "%。");
                    advice.setActions(JSONUtil.toJsonStr(List.of(
                            "密切监视负荷变化趋势", "准备调峰预案",
                            "如持续上升考虑需求响应", "汇报调度主管")));
                    advice.setEvidence(JSONUtil.toJsonStr(evidence));
                } else {
                    advice.setAnalysis("系统检测到负荷越限告警，建议核查遥测数据源和告警规则配置。");
                    advice.setActions(JSONUtil.toJsonStr(List.of(
                            "检查数据采集通道状态", "核验告警阈值配置",
                            "检查 WebSocket 推送延迟", "查看历史趋势确认是否为持续异常",
                            "建议核查相关设备运行状态")));
                    advice.setEvidence(JSONUtil.toJsonStr(evidence));
                }
                advice.setModelName("FALLBACK");
                alertAdviceMapper.updateById(advice);
            } catch (Exception ex) {
                log.error("降级建议写入失败", ex);
            }
        }
    }

    private Map<String, Object> buildEvidence(AlertEvent alert) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("alertId", alert.getId());
        ev.put("level", alert.getLevel());
        ev.put("type", alert.getType());
        ev.put("currentLoad", alert.getCurrentValue());
        ev.put("threshold", alert.getThresholdValue());
        if (alert.getThresholdValue() != null && alert.getThresholdValue() > 0) {
            float ratio = (alert.getCurrentValue() - alert.getThresholdValue())
                    / alert.getThresholdValue() * 100;
            ev.put("overRatio", String.format("%.1f%%", ratio));
        }
        ev.put("triggerTime", alert.getTriggerTime() != null
                ? alert.getTriggerTime().toString() : "N/A");
        ev.put("dataSource", "MOCK_RUNTIME");
        return ev;
    }

    private String buildAdvicePrompt(String evidenceJson) {
        return """
            基于以下告警证据，为调度员(DISPATCHER)和运维管理员(OPERATOR)分别生成建议。
            返回严格JSON格式（不要markdown代码块包裹）：
            {"dispatcher":{"analysis":"分析结论","actions":["措施1","措施2"]},"operator":{"analysis":"分析结论","actions":["措施1","措施2"]}}

            告警证据：
            @@EVIDENCE@@

            要求：
            - dispatcher.analysis: 关注负荷趋势、风险结论、调峰准备，50-150字
            - dispatcher.actions: 3-5条具体措施，每条15-40字
            - operator.analysis: 关注数据质量、规则配置、系统状态，50-150字
            - operator.actions: 3-5条具体措施，每条15-40字
            - 所有建议仅供人工决策参考
            - 数据为MOCK模拟数据，在analysis中说明
            - 不得声称已执行任何操作
            - 不编造设备故障、线路跳闸等未经工具确认的信息
            """.replace("@@EVIDENCE@@", evidenceJson);
    }

    private static String trunc(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
