package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.entity.WeatherForecast;
import com.powerload.mapper.WeatherForecastMapper;
import com.powerload.service.WeatherForecastService;
import com.powerload.weather.WeatherProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherForecastServiceImpl implements WeatherForecastService {

    private final WeatherForecastMapper weatherForecastMapper;
    private final WeatherProvider weatherProvider;

    @Value("${weather.enabled:true}")
    private boolean enabled;

    @Value("${weather.location-code:DEFAULT}")
    private String locationCode;

    @Value("${weather.forecast-hours:48}")
    private int forecastHours;

    @Override
    public List<WeatherForecast> getForecast(LocalDateTime start, LocalDateTime end) {
        List<WeatherForecast> cached = queryLatest(start, end);
        long expected = Math.max(1, java.time.Duration.between(start, end).toHours() + 1);
        if (enabled && cached.size() < expected) {
            try {
                syncForecast();
                cached = queryLatest(start, end);
            } catch (Exception e) {
                log.warn("天气预报刷新失败，继续使用缓存: {}", e.getMessage());
            }
        }
        return cached;
    }

    @Override
    @Transactional
    public Map<String, Object> syncForecast() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("source", weatherProvider.source());
        if (!enabled) {
            result.put("status", "DISABLED");
            result.put("points", 0);
            return result;
        }

        List<WeatherProvider.WeatherPoint> points = weatherProvider.fetch(forecastHours);
        LocalDateTime issuedAt = LocalDateTime.now().withSecond(0).withNano(0);
        weatherForecastMapper.delete(new LambdaQueryWrapper<WeatherForecast>()
                .eq(WeatherForecast::getLocationCode, locationCode)
                .eq(WeatherForecast::getIssuedAt, issuedAt));

        int saved = 0;
        for (WeatherProvider.WeatherPoint point : points) {
            WeatherForecast row = new WeatherForecast();
            row.setLocationCode(locationCode);
            row.setForecastTime(point.forecastTime());
            row.setTemperature(point.temperature());
            row.setHumidity(point.humidity());
            row.setSource(weatherProvider.source());
            row.setIssuedAt(issuedAt);
            row.setCreatedAt(LocalDateTime.now());
            weatherForecastMapper.insert(row);
            saved++;
        }
        result.put("status", "SUCCESS");
        result.put("points", saved);
        result.put("issuedAt", issuedAt);
        return result;
    }

    private List<WeatherForecast> queryLatest(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || start.isAfter(end)) return List.of();
        List<WeatherForecast> rows = weatherForecastMapper.selectList(
                new LambdaQueryWrapper<WeatherForecast>()
                        .eq(WeatherForecast::getLocationCode, locationCode)
                        .ge(WeatherForecast::getForecastTime, start)
                        .le(WeatherForecast::getForecastTime, end)
                        .orderByAsc(WeatherForecast::getForecastTime)
                        .orderByDesc(WeatherForecast::getIssuedAt));
        Map<LocalDateTime, WeatherForecast> latestByTime = new LinkedHashMap<>();
        for (WeatherForecast row : rows) {
            latestByTime.putIfAbsent(row.getForecastTime(), row);
        }
        return new ArrayList<>(latestByTime.values());
    }
}
