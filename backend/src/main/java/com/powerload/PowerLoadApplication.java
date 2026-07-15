package com.powerload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 电力负荷预测与智能告警 Agent — 启动类
 */
@SpringBootApplication
@EnableScheduling
public class PowerLoadApplication {

    public static void main(String[] args) {
        SpringApplication.run(PowerLoadApplication.class, args);
    }
}
