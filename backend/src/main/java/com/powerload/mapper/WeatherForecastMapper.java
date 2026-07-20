package com.powerload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powerload.entity.WeatherForecast;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WeatherForecastMapper extends BaseMapper<WeatherForecast> {
}
