package com.example.finance.task;

import com.example.finance.service.ScheduledTaskRunService;
import com.example.finance.service.StockScreenerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class StockScreenerTask {

    private static final Logger log = LoggerFactory.getLogger(StockScreenerTask.class);
    private static final String TASK_NAME = "a-share-stock-screen";
    private static final String TRIGGER_NAME = "scheduled-16:10";

    private final StockScreenerService stockScreenerService;
    private final ScheduledTaskRunService scheduledTaskRunService;
    private final Set<String> marketClosedDates;

    public StockScreenerTask(
        StockScreenerService stockScreenerService,
        ScheduledTaskRunService scheduledTaskRunService,
        @Value("${finance.investment.market-closed-dates:}") String marketClosedDates
    ) {
        this.stockScreenerService = stockScreenerService;
        this.scheduledTaskRunService = scheduledTaskRunService;
        this.marketClosedDates = Arrays.stream(marketClosedDates.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toSet());
    }

    @Scheduled(cron = "${finance.stock-screener.cron:0 10 16 * * MON-FRI}", zone = "Asia/Shanghai")
    public void scanAfterMarketClose() {
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        if (marketClosedDates.contains(today)) {
            log.info("A股收盘选股任务跳过：date={}, reason=market-closed", today);
            return;
        }
        scheduledTaskRunService.run(TASK_NAME, TRIGGER_NAME, () -> {
            log.info("A股收盘选股任务开始：trigger={}", TRIGGER_NAME);
            var summary = stockScreenerService.runScheduledScan(TRIGGER_NAME);
            log.info("A股收盘选股任务完成：trigger={}, summary={}", TRIGGER_NAME, summary);
            return String.valueOf(summary);
        });
    }
}
