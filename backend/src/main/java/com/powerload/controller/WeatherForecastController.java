package com.powerload.controller;

import com.powerload.common.R;
import com.powerload.entity.WeatherForecast;
import com.powerload.service.WeatherForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/weather")
@RequiredArgsConstructor
public class WeatherForecastController {

    private final WeatherForecastService weatherForecastService;

    @GetMapping("/forecast")
    public R<List<WeatherForecast>> forecast(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return R.ok(weatherForecastService.getForecast(start, end));
    }

    @PostMapping("/sync")
    public R<Map<String, Object>> sync() {
        return R.ok(weatherForecastService.syncForecast());
    }
}
