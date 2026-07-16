package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.dto.response.ForecastResponse;
import com.powerload.entity.LoadData;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.ml.FlaskInferenceService;
import com.powerload.service.PredictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 负荷预测服务实现
 *
 * <p>查询最近 200h 数据 → 通过 Flask 推理 → 返回 24h 预测。
 * 返回明确的 forecastStartTime，前端不再依赖本地时钟推算预测起点。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PredictServiceImpl implements PredictService {

    private final LoadDataMapper loadDataMapper;
    private final FlaskInferenceService flaskInferenceService;

    /** Flask 需要至少 168h 的上下文窗口 */
    private static final int WINDOW_HOURS = 200;

    @Override
    public ForecastResponse forecast() {
        // 1. 查询 DB 最近 200h 数据（留余量，Flask 会自动截取最后 168h）
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(WINDOW_HOURS);

        LambdaQueryWrapper<LoadData> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(LoadData::getTime, start)
               .le(LoadData::getTime, end)
               .orderByAsc(LoadData::getTime);
        List<LoadData> rows = loadDataMapper.selectList(wrapper);

        log.debug("查询到 {} 条原始数据 ({} ~ {})", rows.size(),
                rows.isEmpty() ? "N/A" : rows.get(0).getTime(),
                rows.isEmpty() ? "N/A" : rows.get(rows.size() - 1).getTime());

        if (rows.size() < 168) {
            throw new IllegalStateException(
                    String.format("数据不足: 至少需要 168 条，实际 %d 条", rows.size()));
        }

        // 2. 转换为 Flask 需要的原始格式
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<Map<String, Object>> rawData = rows.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", d.getTime() != null ? d.getTime().format(fmt) : null);
            m.put("load_mw", d.getLoadMw());
            m.put("temperature", d.getTemperature());
            m.put("humidity", d.getHumidity());
            m.put("is_holiday", d.getIsHoliday());
            m.put("hour", d.getHour());
            m.put("day_of_week", d.getDayOfWeek());
            m.put("month", d.getMonth());
            return m;
        }).collect(Collectors.toList());

        // 3. 调用 Flask 推理
        List<Double> predictions = flaskInferenceService.forecast(rawData);

        // 4. 封装响应 — 预测从当前整点下一小时开始
        LocalDateTime forecastStart = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);

        ForecastResponse response = new ForecastResponse();
        response.setPredictions(predictions);
        response.setModel("LSTM");
        response.setForecastStartTime(forecastStart);

        double minP = predictions.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double maxP = predictions.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        log.debug("预测完成: {} 个值, 基准时间={}, 范围 [{}, {}]",
                predictions.size(), forecastStart, minP, maxP);
        return response;
    }
}
