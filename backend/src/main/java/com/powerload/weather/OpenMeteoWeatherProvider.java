package com.powerload.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OpenMeteoWeatherProvider implements WeatherProvider {

    private final ObjectMapper objectMapper;
    private final String latitude;
    private final String longitude;
    private final String timezone;
    private final OkHttpClient client;

    public OpenMeteoWeatherProvider(
            ObjectMapper objectMapper,
            @Value("${weather.latitude:39.9042}") String latitude,
            @Value("${weather.longitude:116.4074}") String longitude,
            @Value("${weather.timezone:Asia/Shanghai}") String timezone) {
        this.objectMapper = objectMapper;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timezone = timezone;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String source() {
        return "OPEN_METEO";
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<WeatherPoint> fetch(int forecastHours) {
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.open-meteo.com")
                .addPathSegments("v1/forecast")
                .addQueryParameter("latitude", latitude)
                .addQueryParameter("longitude", longitude)
                .addQueryParameter("hourly", "temperature_2m,relative_humidity_2m")
                .addQueryParameter("forecast_hours", String.valueOf(Math.max(24, forecastHours)))
                .addQueryParameter("timezone", timezone)
                .build();
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "{}" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("weather service returned HTTP " + response.code());
            }
            Map<String, Object> root = objectMapper.readValue(body, Map.class);
            Map<String, Object> hourly = (Map<String, Object>) root.get("hourly");
            if (hourly == null) {
                throw new IllegalStateException("weather response is missing hourly data");
            }
            List<String> times = (List<String>) hourly.get("time");
            List<Number> temperatures = (List<Number>) hourly.get("temperature_2m");
            List<Number> humidities = (List<Number>) hourly.get("relative_humidity_2m");
            if (times == null || temperatures == null || humidities == null) {
                throw new IllegalStateException("weather response is missing temperature or humidity fields");
            }

            List<WeatherPoint> points = new ArrayList<>();
            int size = Math.min(times.size(), Math.min(temperatures.size(), humidities.size()));
            for (int i = 0; i < size; i++) {
                points.add(new WeatherPoint(
                        LocalDateTime.parse(times.get(i), DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        temperatures.get(i) == null ? null : temperatures.get(i).floatValue(),
                        humidities.get(i) == null ? null : humidities.get(i).floatValue()));
            }
            return points;
        } catch (Exception e) {
            log.warn("Open-Meteo weather forecast fetch failed: {}", e.getMessage());
            throw new IllegalStateException("future weather forecast is temporarily unavailable", e);
        }
    }
}
