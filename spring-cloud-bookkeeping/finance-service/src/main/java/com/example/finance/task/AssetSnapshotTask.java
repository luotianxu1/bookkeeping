package com.example.finance.task;

import com.example.finance.service.AssetSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AssetSnapshotTask {

    private static final Logger log = LoggerFactory.getLogger(AssetSnapshotTask.class);

    private final AssetSnapshotService assetSnapshotService;

    public AssetSnapshotTask(AssetSnapshotService assetSnapshotService) {
        this.assetSnapshotService = assetSnapshotService;
    }

    @Scheduled(cron = "${finance.asset-snapshot.cron:0 0 0 * * *}", zone = "Asia/Shanghai")
    public void captureDailyAssetSnapshot() {
        log.info("资产日快照任务开始：trigger=scheduled-midnight");
        try {
            int savedCount = assetSnapshotService.captureDailySnapshots(null);
            log.info("资产日快照任务完成：trigger=scheduled-midnight, savedCount={}", savedCount);
        } catch (Exception ex) {
            log.error("资产日快照任务失败：trigger=scheduled-midnight", ex);
        }
    }
}
