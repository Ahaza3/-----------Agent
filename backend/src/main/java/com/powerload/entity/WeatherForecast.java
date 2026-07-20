package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("weather_forecast")
public class WeatherForecast {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String locationCode;
    private LocalDateTime forecastTime;
    private Float temperature;
    private Float humidity;
    private String source;
    private LocalDateTime issuedAt;
    private LocalDateTime createdAt;
}
