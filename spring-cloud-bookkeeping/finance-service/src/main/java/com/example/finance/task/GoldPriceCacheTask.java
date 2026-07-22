package com.example.finance.task;

import com.example.finance.service.GoldPriceService;
import com.example.finance.service.ScheduledTaskRunService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class GoldPriceCacheTask {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceCacheTask.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final GoldPriceService goldPriceService;
    private final ScheduledTaskRunService scheduledTaskRunService;
    private final long retryDelayMillis;
    private final int maxRetryAttempts;
    private final Set<LocalDate> marketClosedDates;
    // 上金所交易时段（可配置）：日盘 09:00-15:30，夜盘 21:00-次日 02:30。
    private final LocalTime daySessionStart;
    private final LocalTime daySessionEnd;
    private final LocalTime nightSessionStart;
    private final LocalTime nightSessionEnd;
    private volatile boolean running = true;

    public GoldPriceCacheTask(
        GoldPriceService goldPriceService,
        ScheduledTaskRunService scheduledTaskRunService,
        @Value("${finance.gold-price.retry-delay-millis:10000}") long retryDelayMillis,
        @Value("${finance.gold-price.max-retry-attempts:3}") int maxRetryAttempts,
        @Value("${finance.investment.market-closed-dates:}") String marketClosedDates,
        @Value("${finance.gold-price.trading-session.day-start:09:00}") String daySessionStart,
        @Value("${finance.gold-price.trading-session.day-end:15:30}") String daySessionEnd,
        @Value("${finance.gold-price.trading-session.night-start:21:00}") String nightSessionStart,
        @Value("${finance.gold-price.trading-session.night-end:02:30}") String nightSessionEnd
    ) {
        this.goldPriceService = goldPriceService;
        this.scheduledTaskRunService = scheduledTaskRunService;
        this.retryDelayMillis = Math.max(1000L, retryDelayMillis);
        this.maxRetryAttempts = Math.max(1, maxRetryAttempts);
        this.marketClosedDates = Arrays.stream(marketClosedDates.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(LocalDate::parse)
            .collect(Collectors.toSet());
        this.daySessionStart = LocalTime.parse(daySessionStart.trim());
        this.daySessionEnd = LocalTime.parse(daySessionEnd.trim());
        this.nightSessionStart = LocalTime.parse(nightSessionStart.trim());
        this.nightSessionEnd = LocalTime.parse(nightSessionEnd.trim());
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${finance.gold-price.refresh-interval-millis:300000}")
    public void refreshGoldPriceCache() {
        LocalDateTime now = LocalDateTime.now(MARKET_ZONE);
        if (!isWithinTradingSession(now)) {
            log.debug("黄金缓存刷新跳过：非交易时段，now={}", now);
            return;
        }
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

    // 上金所交易时段判断：仅在交易日的日盘或夜盘刷新，其余时间完全不刷新。
    // 夜盘跨零点，需分三段判断：日盘、夜盘前段（当日 21:00 起）、夜盘尾段（次日 00:00-02:30，归属前一交易日）。
    private boolean isWithinTradingSession(LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        // 日盘 09:00-15:30：要求当天为交易日。
        if (!time.isBefore(daySessionStart) && !time.isAfter(daySessionEnd)) {
            return isTradingDay(date);
        }
        // 夜盘前段 21:00-23:59：夜盘归属当天这个交易日。
        if (!time.isBefore(nightSessionStart)) {
            return isTradingDay(date);
        }
        // 夜盘尾段 00:00-02:30：归属前一日的夜盘，要求前一日为交易日。
        if (!time.isAfter(nightSessionEnd)) {
            return isTradingDay(date.minusDays(1));
        }
        return false;
    }

    private boolean isTradingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }
        return !marketClosedDates.contains(date);
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
