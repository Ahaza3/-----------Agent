package com.powerload.alert;

import org.springframework.stereotype.Component;

/**
 * 🚫 NFZ-3 禁飞区 · 告警文案模板
 *
 * <p>职责：根据告警级别和阈值生成固定中文字符串文案。
 * 不用 LLM —— 零延迟、零成本、文本可控。</p>
 *
 * <p>三类模板：
 * <ul>
 *   <li>aiAnalysis — 告警描述，显示在告警详情中</li>
 *   <li>suggestion  — 建议措施，供调度员参考</li>
 * </ul>
 *
 * <p>设计思路：
 * 告警文本是高度结构化的 — 负荷值、阈值、倍数关系都是固定的数字填入。
 * 用固定模板比 LLM 更可靠：不会出现幻觉、不会被 API 限流、无延迟。
 */
@Component
public class AlertTemplate {

    /**
     * 生成告警分析文案
     *
     * @param level          告警级别 (RED/ORANGE/YELLOW)
     * @param currentValue   当前负荷值 (MW)
     * @param thresholdValue 触发阈值 (MW)
     * @return 中文告警描述
     */
    public String generateAnalysis(String level, float currentValue, float thresholdValue) {
        float exceedRatio = (currentValue - thresholdValue) / thresholdValue * 100;
        String ratioStr = String.format("%.1f", Math.abs(exceedRatio));

        return switch (level) {
            case "RED" -> String.format(
                    "⚠️ 紧急告警：当前负荷 %.0f MW，超出安全上限 %.0f MW 的 %.1s%%，"
                            + "已触发红色预警。建议立即启动调峰预案，关注后续负荷变化趋势。",
                    currentValue, thresholdValue, ratioStr
            );
            case "ORANGE" -> String.format(
                    "🔶 重要告警：当前负荷 %.0f MW，已达到安全上限 %.0f MW 的 %.1s%%，"
                            + "触发橙色预警。请密切关注负荷变化，必要时启动调峰措施。",
                    currentValue, thresholdValue, ratioStr
            );
            case "YELLOW" -> String.format(
                    "🟡 提示告警：当前负荷 %.0f MW，接近安全上限 %.0f MW（已达 %.1s%%），"
                            + "触发黄色预警。建议加强监控，做好调峰准备。",
                    currentValue, thresholdValue, ratioStr
            );
            default -> String.format("当前负荷 %.0f MW，阈值 %.0f MW。", currentValue, thresholdValue);
        };
    }

    /**
     * 生成建议措施
     */
    public String generateSuggestion(String level) {
        return switch (level) {
            case "RED" -> "建议立即启动调峰预案：1) 通知发电侧增加出力 "
                    + "2) 必要时启动需求侧响应 3) 通知值班调度长";
            case "ORANGE" -> "建议启动调峰准备：1) 密切关注负荷变化趋势 "
                    + "2) 通知发电侧做好增发准备 3) 通知值班调度员";
            case "YELLOW" -> "建议加强监控：1) 关注后续负荷变化 "
                    + "2) 检查发电计划是否满足需求 3) 保持通信畅通";
            default -> "暂无建议";
        };
    }
}
