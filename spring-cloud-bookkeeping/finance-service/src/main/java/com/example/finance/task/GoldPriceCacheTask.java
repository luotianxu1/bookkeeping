package com.example.finance.task;

import com.example.finance.service.GoldPriceService;
import com.example.finance.service.ScheduledTaskRunService;
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
    private final ScheduledTaskRunService scheduledTaskRunService;
    private final long retryDelayMillis;
    private final int maxRetryAttempts;
    private volatile boolean running = true;

    public GoldPriceCacheTask(
        GoldPriceService goldPriceService,
        ScheduledTaskRunService scheduledTaskRunService,
        @Value("${finance.gold-price.retry-delay-millis:10000}") long retryDelayMillis,
        @Value("${finance.gold-price.max-retry-attempts:3}") int maxRetryAttempts
    ) {
        this.goldPriceService = goldPriceService;
        this.scheduledTaskRunService = scheduledTaskRunService;
        this.retryDelayMillis = Math.max(1000L, retryDelayMillis);
        this.maxRetryAttempts = Math.max(1, maxRetryAttempts);
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${finance.gold-price.refresh-interval-millis:300000}")
    public void refreshGoldPriceCache() {
        scheduledTaskRunService.run("gold-price-cache-refresh", "fixed-delay", () -> {
            Exception lastFailure = null;
            for (int attempt = 1; running && attempt <= maxRetryAttempts; attempt++) {
                try {
                    goldPriceService.refreshCache();
                    log.info("黄金缓存刷新完成：attempt={}", attempt);
                    return "attempt=" + attempt;
                } catch (Exception ex) {
                    lastFailure = ex;
                    log.warn("黄金缓存刷新失败：attempt={}, retryDelayMillis={}", attempt, retryDelayMillis, ex);
                    if (attempt < maxRetryAttempts) {
                        sleepBeforeRetry();
                    }
                }
            }
            log.warn("黄金缓存刷新已放弃本轮重试：maxRetryAttempts={}", maxRetryAttempts);
            throw new IllegalStateException("黄金缓存刷新失败，已达到最大重试次数", lastFailure);
        });
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
