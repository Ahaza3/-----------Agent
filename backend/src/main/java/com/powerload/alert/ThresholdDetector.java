package com.powerload.alert;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 🚫 NFZ-3 禁飞区 · 告警检测引擎
 *
 * <p>职责：根据告警规则配置判断当前负荷是否触发告警，返回告警级别。
 * 仅做纯数学计算，不涉及 LLM 调用。</p>
 *
 * <p>规则配置 JSON 格式：
 * <pre>{@code
 * {
 *   "threshold": 1200,     // 安全上限 MW
 *   "redRatio": 1.10,      // 红色告警系数
 *   "orangeRatio": 1.00,   // 橙色告警系数
 *   "yellowRatio": 0.90,   // 黄色告警系数
 *   "coolingTime": 3600    // 冷却时间（秒），防重复告警
 * }
 * }</pre>
 *
 * <p>检测逻辑：
 * <ul>
 *   <li>current > threshold × redRatio    → RED</li>
 *   <li>current > threshold × orangeRatio → ORANGE</li>
 *   <li>current > threshold × yellowRatio → YELLOW</li>
 *   <li>否则 → null（未触发）</li>
 * </ul>
 * 多条命中时返回最严重的级别。
 */
@Slf4j
@Component
public class ThresholdDetector {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 检测是否触发告警，返回告警级别
     *
     * @param currentLoad  当前负荷值 (MW)
     * @param configJson   告警规则配置 (JSON 字符串)
     * @return RED / ORANGE / YELLOW / null
     */
    public String detect(float currentLoad, String configJson) {
        Map<String, Object> config = parseConfig(configJson);
        if (config == null) return null;

        float threshold = getFloat(config, "threshold");
        float redRatio = getFloat(config, "redRatio", 1.10f);
        float orangeRatio = getFloat(config, "orangeRatio", 1.00f);
        float yellowRatio = getFloat(config, "yellowRatio", 0.90f);

        if (threshold <= 0) return null;

        if (currentLoad > threshold * redRatio) {
            log.debug("触发 RED: 当前={}MW > 阈值={}MW × {}", currentLoad, threshold, redRatio);
            return "RED";
        }
        if (currentLoad > threshold * orangeRatio) {
            log.debug("触发 ORANGE: 当前={}MW > 阈值={}MW × {}", currentLoad, threshold, orangeRatio);
            return "ORANGE";
        }
        if (currentLoad > threshold * yellowRatio) {
            log.debug("触发 YELLOW: 当前={}MW > 阈值={}MW × {}", currentLoad, threshold, yellowRatio);
            return "YELLOW";
        }
        return null;
    }

    /**
     * 获取规则中的冷却时间（秒），默认 3600
     */
    public int getCoolingTime(String configJson) {
        Map<String, Object> config = parseConfig(configJson);
        if (config == null) return 3600;
        Object ct = config.get("coolingTime");
        if (ct instanceof Number) return ((Number) ct).intValue();
        return 3600;
    }

    /** 获取规则中的安全阈值 */
    public float getThreshold(String configJson) {
        Map<String, Object> config = parseConfig(configJson);
        if (config == null) return 0;
        return getFloat(config, "threshold");
    }

    private Map<String, Object> parseConfig(String configJson) {
        try {
            return MAPPER.readValue(configJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("解析告警规则配置失败: {}", configJson, e);
            return null;
        }
    }

    private float getFloat(Map<String, Object> map, String key) {
        return getFloat(map, key, 0f);
    }

    private float getFloat(Map<String, Object> map, String key, float defaultVal) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).floatValue();
        return defaultVal;
    }
}
