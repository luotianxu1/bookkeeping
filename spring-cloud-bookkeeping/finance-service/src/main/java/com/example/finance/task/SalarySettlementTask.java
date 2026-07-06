package com.example.finance.task;

import com.example.finance.service.SalaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SalarySettlementTask {

    private static final Logger log = LoggerFactory.getLogger(SalarySettlementTask.class);

    private final SalaryService salaryService;

    public SalarySettlementTask(SalaryService salaryService) {
        this.salaryService = salaryService;
    }

    @Scheduled(cron = "${finance.salary.settlement.cron:0 10 8 * * *}", zone = "Asia/Shanghai")
    public void settleDueSalaryAccounts() {
        log.info("工资账户发薪入账任务开始：trigger=scheduled-08:10");
        try {
            salaryService.settleDueAccountRecordsForAllUsers();
            log.info("工资账户发薪入账任务完成：trigger=scheduled-08:10");
        } catch (Exception ex) {
            log.error("工资账户发薪入账任务失败：trigger=scheduled-08:10", ex);
        }
    }
}
