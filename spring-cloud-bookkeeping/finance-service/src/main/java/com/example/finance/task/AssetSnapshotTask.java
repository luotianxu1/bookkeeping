package com.example.finance.task;

import com.example.finance.service.AssetSnapshotService;
import com.example.finance.service.ScheduledTaskRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AssetSnapshotTask {

    private static final Logger log = LoggerFactory.getLogger(AssetSnapshotTask.class);

    private final AssetSnapshotService assetSnapshotService;
    private final ScheduledTaskRunService scheduledTaskRunService;

    public AssetSnapshotTask(AssetSnapshotService assetSnapshotService, ScheduledTaskRunService scheduledTaskRunService) {
        this.assetSnapshotService = assetSnapshotService;
        this.scheduledTaskRunService = scheduledTaskRunService;
    }

    @Scheduled(cron = "${finance.asset-snapshot.cron:0 0 0 * * *}", zone = "Asia/Shanghai")
    public void captureDailyAssetSnapshot() {
        scheduledTaskRunService.run("asset-daily-snapshot", "scheduled-00:00", () -> {
            log.info("资产日快照任务开始：trigger=scheduled-00:00");
            int savedCount = assetSnapshotService.captureDailySnapshots(null);
            log.info("资产日快照任务完成：trigger=scheduled-00:00, savedCount={}", savedCount);
            return "savedCount=" + savedCount;
        });
    }

    @Scheduled(cron = "${finance.asset-snapshot.retry-cron:0 10 0 * * *}", zone = "Asia/Shanghai")
    public void retryDailyAssetSnapshot() {
        scheduledTaskRunService.run("asset-daily-snapshot", "scheduled-00:10", () -> {
            log.info("资产日快照补偿任务开始：trigger=scheduled-00:10");
            int savedCount = assetSnapshotService.captureDailySnapshots(null);
            log.info("资产日快照补偿任务完成：trigger=scheduled-00:10, savedCount={}", savedCount);
            return "savedCount=" + savedCount;
        });
    }
}
