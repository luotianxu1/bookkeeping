package com.example.app.task;

import com.example.app.service.MarketCalendarService;
import com.example.app.service.ScheduledTaskRunService;
import com.example.app.service.StockScreenerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class StockScreenerTask {

    private static final Logger log = LoggerFactory.getLogger(StockScreenerTask.class);
    private static final String TASK_NAME = "a-share-stock-screen";
    private static final String TRIGGER_NAME = "scheduled-16:10";

    private final StockScreenerService stockScreenerService;
    private final ScheduledTaskRunService scheduledTaskRunService;
    private final MarketCalendarService marketCalendarService;

    public StockScreenerTask(
        StockScreenerService stockScreenerService,
        ScheduledTaskRunService scheduledTaskRunService,
        MarketCalendarService marketCalendarService
    ) {
        this.stockScreenerService = stockScreenerService;
        this.scheduledTaskRunService = scheduledTaskRunService;
        this.marketCalendarService = marketCalendarService;
    }

    @Scheduled(cron = "${finance.stock-screener.cron:0 10 16 * * MON-FRI}", zone = "Asia/Shanghai")
    public void scanAfterMarketClose() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if (marketCalendarService.isClosed(today)) {
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
