package com.example.finance.task;

import com.example.finance.service.ScheduledTaskRunService;
import com.example.finance.service.SalaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SalarySettlementTask {

    private static final Logger log = LoggerFactory.getLogger(SalarySettlementTask.class);

    private final SalaryService salaryService;
    private final ScheduledTaskRunService scheduledTaskRunService;

    public SalarySettlementTask(SalaryService salaryService, ScheduledTaskRunService scheduledTaskRunService) {
        this.salaryService = salaryService;
        this.scheduledTaskRunService = scheduledTaskRunService;
    }

    @Scheduled(cron = "${finance.salary.settlement.cron:0 10 8 * * *}", zone = "Asia/Shanghai")
    public void settleDueSalaryRecords() {
        scheduledTaskRunService.run("salary-settlement", "scheduled-08:10", () -> {
            log.info("工资及关联账户发薪入账任务开始：trigger=scheduled-08:10");
            salaryService.settleDueRecordsForAllUsers();
            log.info("工资及关联账户发薪入账任务完成：trigger=scheduled-08:10");
            return "completed";
        });
    }
}
