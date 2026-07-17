package com.powerload.alert;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.agent.AgentMessage;
import com.powerload.agent.LlmClient;
import com.powerload.entity.AlertAdvice;
import com.powerload.entity.AlertEvent;
import com.powerload.mapper.AlertAdviceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public AlertAdviceService(AlertAdviceMapper alertAdviceMapper, LlmClient llmClient) {
        this.alertAdviceMapper = alertAdviceMapper;
        this.llmClient = llmClient;
        // 有界线程池：最多 2 个并发 LLM 调用
        this.executor = new ThreadPoolExecutor(1, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(20),
                r -> new Thread(r, "alert-advice"),
                new ThreadPoolExecutor.DiscardOldestPolicy());

        // 预创建 PENDING 记录以避免重复
        try {
            // 检查是否已有同告警的 PENDING 记录
        } catch (Exception ignored) {}
    }

    @Async
    @EventListener
    public void onAlertCreated(AlertCreatedEvent event) {
        AlertEvent alert = event.getAlert();
        executor.submit(() -> generateAdvice(alert));
    }

    void generateAdvice(AlertEvent alert) {
        Long alertId = alert.getId();
        // 检查是否已有成功记录（去重）
        var existing = alertAdviceMapper.selectList(
                new LambdaQueryWrapper<AlertAdvice>().eq(AlertAdvice::getAlertId, alertId));
        if (!existing.isEmpty() && existing.stream().anyMatch(a -> "SUCCESS".equals(a.getStatus()))) {
            return;
        }

        // 预插入 PENDING
        for (String role : List.of("DISPATCHER", "OPERATOR")) {
            try {
                AlertAdvice advice = new AlertAdvice();
                advice.setAlertId(alertId);
                advice.setAudienceRole(role);
                advice.setStatus("PENDING");
                advice.setCreatedAt(LocalDateTime.now());
                alertAdviceMapper.insert(advice);
            } catch (Exception e) { /* 幂等 */ }
        }

        // 构建 evidence（简化版，真实场景需查更多数据）
        Map<String, Object> evidence = buildEvidence(alert);
        String evidenceJson = JSONUtil.toJsonStr(evidence);

        // 单次 LLM 调用生成两个角色的建议
        try {
            String prompt = buildAdvicePrompt(alert, evidenceJson);
            AgentMessage msg = llmClient.chat(
                    List.of(AgentMessage.system("你是电力系统告警分析专家。只生成指定格式的JSON，不要输出其他内容。")),
                    null);
            // 降级：使用mock/fallback
            throw new RuntimeException("LLM advice not available in mock mode, using fallback");
        } catch (Exception e) {
            log.warn("LLM 建议生成失败，使用降级: alertId={}", alertId, e);
            useFallback(alertId, evidence, e.getMessage());
        }
    }

    void useFallback(Long alertId, Map<String, Object> evidence, String errorMsg) {
        for (String role : List.of("DISPATCHER", "OPERATOR")) {
            try {
                // 更新 PENDING → FALLBACK
                var wrapper = new LambdaQueryWrapper<AlertAdvice>()
                        .eq(AlertAdvice::getAlertId, alertId)
                        .eq(AlertAdvice::getAudienceRole, role);
                AlertAdvice advice = alertAdviceMapper.selectOne(wrapper);
                if (advice == null) continue;

                advice.setStatus("FALLBACK");
                advice.setErrorMessage(errorMsg);
                advice.setGeneratedAt(LocalDateTime.now());

                if ("DISPATCHER".equals(role)) {
                    advice.setAnalysis("负荷" + evidence.get("level") + "告警：当前 "
                            + evidence.get("currentLoad") + " MW，超阈值 "
                            + evidence.get("threshold") + " MW 约 "
                            + evidence.get("overRatio") + "%。");
                    advice.setActions(JSONUtil.toJsonStr(List.of(
                            "密切监视负荷变化趋势",
                            "准备调峰预案",
                            "如持续上升考虑需求响应",
                            "汇报调度主管")));
                    advice.setEvidence(JSONUtil.toJsonStr(evidence));
                } else {
                    advice.setAnalysis("系统检测到负荷越限告警，建议核查遥测数据源和告警规则配置。");
                    advice.setActions(JSONUtil.toJsonStr(List.of(
                            "检查数据采集通道状态",
                            "核验告警阈值配置",
                            "检查 WebSocket 推送延迟",
                            "查看历史趋势确认是否为持续异常",
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
            float overRatio = (alert.getCurrentValue() - alert.getThresholdValue()) / alert.getThresholdValue() * 100;
            ev.put("overRatio", String.format("%.1f%%", overRatio));
        }
        ev.put("triggerTime", alert.getTriggerTime() != null ? alert.getTriggerTime().toString() : "N/A");
        ev.put("dataSource", "MOCK_RUNTIME");
        return ev;
    }

    private String buildAdvicePrompt(AlertEvent alert, String evidenceJson) {
        return """
            基于以下告警证据，为调度员(DISPATCHER)和运维管理员(OPERATOR)分别生成建议。
            返回严格JSON格式：{"dispatcher":{"analysis":"...","actions":["..."]},"operator":{"analysis":"...","actions":["..."]}}

            证据：%s

            要求：
            - 调度员：关注负荷趋势、调峰准备、人工确认项
            - 运维管理员：关注数据质量、规则配置、系统状态
            - 所有建议仅供人工决策参考，不得声称已执行任何操作
            - 明确标注数据为模拟数据
            """.formatted(evidenceJson);
    }
}
