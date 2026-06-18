package com.example.finance.task;

import com.example.finance.service.InvestmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FundQuoteSyncTask {

    private static final Logger log = LoggerFactory.getLogger(FundQuoteSyncTask.class);

    private final InvestmentService investmentService;

    public FundQuoteSyncTask(InvestmentService investmentService) {
        this.investmentService = investmentService;
    }

    @Scheduled(cron = "${finance.investment.profit-sync.cron:0 30 21 * * *}", zone = "Asia/Shanghai")
    public void syncDailyFundProfits() {
        log.info("投资夜间同步任务开始：trigger=scheduled-21:30");
        try {
            log.info("投资夜间同步任务完成：trigger=scheduled-21:30, summary={}",
                investmentService.runInvestmentSyncCycle("scheduled-21:30"));
        } catch (Exception ex) {
            log.error("投资夜间同步任务失败：trigger=scheduled-21:30", ex);
            throw ex;
        }
    }

    @Scheduled(cron = "${finance.investment.auto-invest.cron:0 5 9 * * *}", zone = "Asia/Shanghai")
    public void executeAutoInvestPlans() {
        log.info("基金定投与确认任务开始：trigger=scheduled-09:05");
        try {
            int executedCount = investmentService.executeDueAutoInvestPlans();
            int settledCount = investmentService.settlePendingFundTrades();
            log.info("基金定投与确认任务完成：trigger=scheduled-09:05, executedCount={}, settledCount={}", executedCount, settledCount);
        } catch (Exception ex) {
            log.error("基金定投与确认任务失败：trigger=scheduled-09:05", ex);
            throw ex;
        }
    }

    @Scheduled(cron = "${finance.investment.trade-settlement.cron:0 6 9 * * *}", zone = "Asia/Shanghai")
    public void settleFundTrades() {
        log.info("基金交易补结算任务开始：trigger=scheduled-09:06");
        try {
            int settledCount = investmentService.settlePendingFundTrades();
            log.info("基金交易补结算任务完成：trigger=scheduled-09:06, settledCount={}", settledCount);
        } catch (Exception ex) {
            log.error("基金交易补结算任务失败：trigger=scheduled-09:06", ex);
            throw ex;
        }
    }
}
