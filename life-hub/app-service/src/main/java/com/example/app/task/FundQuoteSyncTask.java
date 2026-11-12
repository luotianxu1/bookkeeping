package com.example.app.task;

import com.example.app.service.InvestmentService;
import com.example.app.service.ScheduledTaskRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FundQuoteSyncTask {

    private static final Logger log = LoggerFactory.getLogger(FundQuoteSyncTask.class);

    private final InvestmentService investmentService;
    private final ScheduledTaskRunService scheduledTaskRunService;

    public FundQuoteSyncTask(InvestmentService investmentService, ScheduledTaskRunService scheduledTaskRunService) {
        this.investmentService = investmentService;
        this.scheduledTaskRunService = scheduledTaskRunService;
    }

    @Scheduled(cron = "${finance.investment.profit-sync.cron:0 30 21 * * *}", zone = "Asia/Shanghai")
    public void syncDailyFundProfits() {
        scheduledTaskRunService.run("investment-night-sync", "scheduled-21:30", () -> {
            log.info("投资夜间同步任务开始：trigger=scheduled-21:30");
            var summary = investmentService.runNightlyInvestmentSyncCycle("scheduled-21:30");
            log.info("投资夜间同步任务完成：trigger=scheduled-21:30, summary={}", summary);
            return String.valueOf(summary);
        });
    }

    /**
     * 夜间净值补抓：新浪通道当日净值常晚于 21:30 发布，主同步后按补抓 cron 自动重跑。
     * 每次执行前先校验当日净值是否已落库（hasStaleFundNavForToday：交易日 + 逐产品最新净值日期），
     * 已覆盖则直接跳过，未覆盖才重跑整个夜间同步周期。
     */
    @Scheduled(cron = "${finance.investment.profit-sync.retry-cron:0 5,35 22,23 * * *}", zone = "Asia/Shanghai")
    public void retryStaleFundNavSync() {
        if (!investmentService.hasStaleFundNavForToday()) {
            return;
        }
        scheduledTaskRunService.run("investment-night-sync", "scheduled-nav-retry", () -> {
            log.info("基金净值补抓任务开始：trigger=scheduled-nav-retry");
            var summary = investmentService.runNightlyInvestmentSyncCycle("scheduled-nav-retry");
            log.info("基金净值补抓任务完成：trigger=scheduled-nav-retry, summary={}", summary);
            return String.valueOf(summary);
        });
    }

    @Scheduled(cron = "${finance.investment.auto-invest.cron:0 5 9 * * *}", zone = "Asia/Shanghai")
    public void executeAutoInvestPlans() {
        scheduledTaskRunService.run("investment-auto-invest", "scheduled-09:05", () -> {
            log.info("基金定投与确认任务开始：trigger=scheduled-09:05");
            int executedCount = investmentService.executeDueAutoInvestPlans();
            int settledCount = investmentService.settlePendingFundTrades();
            log.info("基金定投与确认任务完成：trigger=scheduled-09:05, executedCount={}, settledCount={}", executedCount, settledCount);
            return "executedCount=" + executedCount + ", settledCount=" + settledCount;
        });
    }

    @Scheduled(cron = "${finance.investment.trade-settlement.cron:0 6 9 * * *}", zone = "Asia/Shanghai")
    public void settleFundTrades() {
        scheduledTaskRunService.run("investment-trade-settlement", "scheduled-09:06", () -> {
            log.info("基金交易补结算任务开始：trigger=scheduled-09:06");
            int settledCount = investmentService.settlePendingFundTrades();
            log.info("基金交易补结算任务完成：trigger=scheduled-09:06, settledCount={}", settledCount);
            return "settledCount=" + settledCount;
        });
    }
}
