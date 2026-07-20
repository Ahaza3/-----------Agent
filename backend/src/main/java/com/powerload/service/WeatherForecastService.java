package com.powerload.service;

import com.powerload.entity.WeatherForecast;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface WeatherForecastService {

    List<WeatherForecast> getForecast(LocalDateTime start, LocalDateTime end);

    Map<String, Object> syncForecast();
}
