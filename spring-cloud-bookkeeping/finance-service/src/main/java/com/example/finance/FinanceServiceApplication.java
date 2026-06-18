package com.example.finance;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.example.finance.mapper")
@EnableAsync
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.example")
public class FinanceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceServiceApplication.class, args);
    }
}
