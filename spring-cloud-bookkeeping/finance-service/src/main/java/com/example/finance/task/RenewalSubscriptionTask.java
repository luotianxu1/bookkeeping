package com.example.finance.task;

import com.example.finance.service.RenewalSubscriptionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class RenewalSubscriptionTask {

    private static final ZoneId SHANGHAI_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final RenewalSubscriptionService renewalSubscriptionService;

    public RenewalSubscriptionTask(RenewalSubscriptionService renewalSubscriptionService) {
        this.renewalSubscriptionService = renewalSubscriptionService;
    }

    @Scheduled(cron = "${finance.renewal.auto-deduct.cron:0 0 8 * * *}", zone = "Asia/Shanghai")
    public void executeDueRenewals() {
        LocalDate today = LocalDate.now(SHANGHAI_ZONE_ID);
        for (Long subscriptionId : renewalSubscriptionService.listDueSubscriptionIds(today)) {
            renewalSubscriptionService.processDueSubscription(subscriptionId);
        }
    }
}
