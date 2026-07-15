package com.example.finance.task;

import com.example.finance.service.AssetSnapshotService;
import com.example.finance.service.ScheduledTaskRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class AssetSnapshotTask {

    private static final Logger log = LoggerFactory.getLogger(AssetSnapshotTask.class);
    private static final ZoneId SNAPSHOT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String TASK_NAME = "asset-daily-snapshot";
    private static final String DAILY_TRIGGER = "scheduled-00:00";
    private static final String RETRY_TRIGGER = "scheduled-00:10";

    private final AssetSnapshotService assetSnapshotService;
    private final ScheduledTaskRunService scheduledTaskRunService;

    public AssetSnapshotTask(AssetSnapshotService assetSnapshotService, ScheduledTaskRunService scheduledTaskRunService) {
        this.assetSnapshotService = assetSnapshotService;
        this.scheduledTaskRunService = scheduledTaskRunService;
    }

    @Scheduled(cron = "${finance.asset-snapshot.cron:0 0 0 * * *}", zone = "Asia/Shanghai")
    public void captureDailyAssetSnapshot() {
        LocalDate snapshotDate = resolveScheduledSnapshotDate();
        scheduledTaskRunService.run(TASK_NAME, DAILY_TRIGGER, () -> {
            log.info("资产日快照任务开始：trigger={}, snapshotDate={}", DAILY_TRIGGER, snapshotDate);
            int savedCount = assetSnapshotService.captureDailySnapshots(snapshotDate);
            log.info("资产日快照任务完成：trigger={}, snapshotDate={}, savedCount={}", DAILY_TRIGGER, snapshotDate, savedCount);
            return "savedCount=" + savedCount;
        });
    }

    @Scheduled(cron = "${finance.asset-snapshot.retry-cron:0 10 0 * * *}", zone = "Asia/Shanghai")
    public void retryDailyAssetSnapshot() {
        LocalDate snapshotDate = resolveScheduledSnapshotDate();
        LocalDateTime scheduledMidnight = snapshotDate.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now(SNAPSHOT_ZONE);
        if (scheduledTaskRunService.hasSuccessfulRunBetween(
            TASK_NAME,
            DAILY_TRIGGER,
            scheduledMidnight.minusMinutes(1),
            now.plusSeconds(1)
        )) {
            log.info("资产日快照补偿任务跳过：trigger={}, snapshotDate={}, reason=scheduled-00:00-success", RETRY_TRIGGER, snapshotDate);
            return;
        }
        scheduledTaskRunService.run(TASK_NAME, RETRY_TRIGGER, () -> {
            log.info("资产日快照补偿任务开始：trigger={}, snapshotDate={}", RETRY_TRIGGER, snapshotDate);
            int savedCount = assetSnapshotService.captureDailySnapshots(snapshotDate);
            log.info("资产日快照补偿任务完成：trigger={}, snapshotDate={}, savedCount={}", RETRY_TRIGGER, snapshotDate, savedCount);
            return "savedCount=" + savedCount;
        });
    }

    private LocalDate resolveScheduledSnapshotDate() {
        return LocalDateTime.now(SNAPSHOT_ZONE).plusSeconds(5).toLocalDate().minusDays(1);
    }
}
