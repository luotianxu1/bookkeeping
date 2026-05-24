package com.example.finance.task;

import com.example.finance.service.InvestmentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FundQuoteSyncTask {

    private final InvestmentService investmentService;

    public FundQuoteSyncTask(InvestmentService investmentService) {
        this.investmentService = investmentService;
    }

    @Scheduled(cron = "${finance.investment.profit-sync.cron:0 30 21 * * *}", zone = "Asia/Shanghai")
    public void syncDailyFundProfits() {
        investmentService.syncDailyFundProfits();
    }

    @Scheduled(cron = "${finance.investment.auto-invest.cron:0 5 9 * * *}", zone = "Asia/Shanghai")
    public void executeAutoInvestPlans() {
        investmentService.executeDueAutoInvestPlans();
    }

    @Scheduled(cron = "${finance.investment.trade-settlement.cron:0 0 0 * * *}", zone = "Asia/Shanghai")
    public void settleFundTrades() {
        investmentService.settlePendingFundTrades();
    }
}
