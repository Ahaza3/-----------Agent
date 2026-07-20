package com.powerload.weather;

import java.time.LocalDateTime;
import java.util.List;

public interface WeatherProvider {

    String source();

    List<WeatherPoint> fetch(int forecastHours);

    record WeatherPoint(LocalDateTime forecastTime, Float temperature, Float humidity) {
    }
}
