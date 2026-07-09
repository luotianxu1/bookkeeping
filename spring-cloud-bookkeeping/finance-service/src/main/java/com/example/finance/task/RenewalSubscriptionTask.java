package com.example.finance.task;

import com.example.finance.service.RenewalSubscriptionService;
import com.example.finance.service.ScheduledTaskRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class RenewalSubscriptionTask {

    private static final Logger log = LoggerFactory.getLogger(RenewalSubscriptionTask.class);
    private static final ZoneId SHANGHAI_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final RenewalSubscriptionService renewalSubscriptionService;
    private final ScheduledTaskRunService scheduledTaskRunService;

    public RenewalSubscriptionTask(
        RenewalSubscriptionService renewalSubscriptionService,
        ScheduledTaskRunService scheduledTaskRunService
    ) {
        this.renewalSubscriptionService = renewalSubscriptionService;
        this.scheduledTaskRunService = scheduledTaskRunService;
    }

    @Scheduled(cron = "${finance.renewal.auto-deduct.cron:0 0 8 * * *}", zone = "Asia/Shanghai")
    public void executeDueRenewals() {
        scheduledTaskRunService.run("renewal-auto-deduct", "scheduled-08:00", () -> {
            LocalDate today = LocalDate.now(SHANGHAI_ZONE_ID);
            var subscriptionIds = renewalSubscriptionService.listDueSubscriptionIds(today);
            log.info("续费自动扣费任务开始：trigger=scheduled-08:00, dueCount={}", subscriptionIds.size());
            for (Long subscriptionId : subscriptionIds) {
                renewalSubscriptionService.processDueSubscription(subscriptionId);
            }
            log.info("续费自动扣费任务完成：trigger=scheduled-08:00, processedCount={}", subscriptionIds.size());
            return "processedCount=" + subscriptionIds.size();
        });
    }
}
