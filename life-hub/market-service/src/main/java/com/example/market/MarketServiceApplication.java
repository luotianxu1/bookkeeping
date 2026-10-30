package com.example.market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 行情中心服务：全球行情/涨跌停/市场状态/要闻/美股盘前。
 *
 * <p>行情聚合（新浪/腾讯/东财/纳斯达克公开源）+ 与 app-service 同一套安全体系：
 * 全量扫描 com.example 复用 common 的 JWT 过滤器、InternalApiKeyFilter 与 DefaultSecurityConfig
 * （/api/market/** 需登录，/api/internal/** 走 X-Internal-Key）。用户状态校验（UserStatusChecker，
 * 30 秒缓存）连同一 MySQL，无业务表，仅 jdbc 只读访问。</p>
 */
@SpringBootApplication(scanBasePackages = "com.example")
@EnableScheduling
public class MarketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketServiceApplication.class, args);
    }
}
