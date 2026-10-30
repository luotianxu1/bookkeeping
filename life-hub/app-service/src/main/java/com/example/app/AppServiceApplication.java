package com.example.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan({"com.example.app.mapper", "com.example.tool.mapper", "com.example.food.mapper"})
@EnableAsync
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.example")
public class AppServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppServiceApplication.class, args);
    }
}
