package com.example.finance.task;

import com.example.finance.service.GoldPriceService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GoldPriceCacheTask {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceCacheTask.class);

    private final GoldPriceService goldPriceService;
    private final long retryDelayMillis;
    private volatile boolean running = true;

    public GoldPriceCacheTask(
        GoldPriceService goldPriceService,
        @Value("${finance.gold-price.retry-delay-millis:10000}") long retryDelayMillis
    ) {
        this.goldPriceService = goldPriceService;
        this.retryDelayMillis = Math.max(1000L, retryDelayMillis);
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${finance.gold-price.refresh-interval-millis:300000}")
    public void refreshGoldPriceCache() {
        int attempt = 0;
        while (running) {
            attempt++;
            try {
                goldPriceService.refreshCache();
                log.info("黄金缓存刷新完成：attempt={}", attempt);
                return;
            } catch (Exception ex) {
                log.warn("黄金缓存刷新失败：attempt={}, retryDelayMillis={}", attempt, retryDelayMillis, ex);
                sleepBeforeRetry();
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
