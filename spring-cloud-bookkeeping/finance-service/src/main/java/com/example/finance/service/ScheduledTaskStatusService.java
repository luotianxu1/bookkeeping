package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.ScheduledTaskStatusResponse;
import com.example.finance.entity.ScheduledTaskRunEntity;
import com.example.finance.mapper.ScheduledTaskRunMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ScheduledTaskStatusService {

    private static final ZoneId SHANGHAI_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final ScheduledTaskRunMapper scheduledTaskRunMapper;
    private final ScheduledTaskRunService scheduledTaskRunService;
    private final AssetSnapshotService assetSnapshotService;
    private final RenewalSubscriptionService renewalSubscriptionService;
    private final SalaryService salaryService;
    private final InvestmentService investmentService;
    private final GoldPriceService goldPriceService;
    private final StockScreenerService stockScreenerService;
    private final Set<String> marketClosedDates;

    public ScheduledTaskStatusService(
        ScheduledTaskRunMapper scheduledTaskRunMapper,
        ScheduledTaskRunService scheduledTaskRunService,
        AssetSnapshotService assetSnapshotService,
        RenewalSubscriptionService renewalSubscriptionService,
        SalaryService salaryService,
        InvestmentService investmentService,
        GoldPriceService goldPriceService,
        StockScreenerService stockScreenerService,
        @Value("${finance.investment.market-closed-dates:}") String marketClosedDates
    ) {
        this.scheduledTaskRunMapper = scheduledTaskRunMapper;
        this.scheduledTaskRunService = scheduledTaskRunService;
        this.assetSnapshotService = assetSnapshotService;
        this.renewalSubscriptionService = renewalSubscriptionService;
        this.salaryService = salaryService;
        this.investmentService = investmentService;
        this.goldPriceService = goldPriceService;
        this.stockScreenerService = stockScreenerService;
        this.marketClosedDates = Arrays.stream(marketClosedDates.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toCollection(HashSet::new));
    }

    public List<ScheduledTaskStatusResponse> getTodayTaskStatuses() {
        LocalDate today = LocalDate.now(SHANGHAI_ZONE_ID);
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime nextDayStart = today.plusDays(1).atStartOfDay();
        Map<String, ScheduledTaskRunEntity> latestRuns = scheduledTaskRunMapper.selectList(
                new LambdaQueryWrapper<ScheduledTaskRunEntity>()
                    .ge(ScheduledTaskRunEntity::getStartedAt, dayStart)
                    .lt(ScheduledTaskRunEntity::getStartedAt, nextDayStart)
            ).stream()
            .collect(Collectors.toMap(
                run -> run.getTaskName() + "::" + run.getTriggerName(),
                Function.identity(),
                (left, right) -> latest(left, right)
            ));

        ScheduledTaskRunEntity assetSnapshotRun = latestRuns.get("asset-daily-snapshot::scheduled-00:00");
        return List.of(
            resolveTask("asset-daily-snapshot", "资产日快照", "scheduled-00:00", "00:00", today, LocalTime.MIDNIGHT, latestRuns, false),
            resolveRetryTask(today, assetSnapshotRun, latestRuns),
            resolveTask("renewal-auto-deduct", "固定支出自动扣款", "scheduled-08:00", "08:00", today, LocalTime.of(8, 0), latestRuns, false),
            resolveTask("salary-settlement", "工资入账", "scheduled-08:10", "08:10", today, LocalTime.of(8, 10), latestRuns, false),
            resolveTask("investment-auto-invest", "基金定投与确认", "scheduled-09:05", "09:05", today, LocalTime.of(9, 5), latestRuns, false),
            resolveTask("investment-trade-settlement", "基金交易结算", "scheduled-09:06", "09:06", today, LocalTime.of(9, 6), latestRuns, false),
            resolveGoldPriceTask(today, latestRuns),
            resolveTask("a-share-stock-screen", "A股收盘选股", "scheduled-16:10", "16:10（交易日）", today, LocalTime.of(16, 10), latestRuns, true),
            resolveTask("investment-night-sync", "投资夜间同步", "scheduled-21:30", "21:30", today, LocalTime.of(21, 30), latestRuns, false)
        );
    }

    public ScheduledTaskStatusResponse executeTodayTask(String taskName, String triggerName) {
        LocalDate today = LocalDate.now(SHANGHAI_ZONE_ID);
        LocalTime scheduledTime = resolveScheduledTime(taskName, triggerName);
        if (scheduledTime == null) {
            throw new IllegalArgumentException("不支持手动执行该任务");
        }
        if ("a-share-stock-screen".equals(taskName) && isMarketClosed(today)) {
            throw new IllegalArgumentException("今日为休市日，无法执行A股收盘选股");
        }
        if (LocalDateTime.now(SHANGHAI_ZONE_ID).isBefore(today.atTime(scheduledTime))) {
            throw new IllegalArgumentException("未到计划执行时间");
        }
        ScheduledTaskStatusResponse currentTask = getTodayTaskStatuses().stream()
            .filter(task -> taskName.equals(task.getTaskName()) && triggerName.equals(task.getTriggerName()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        if ("running".equals(currentTask.getStatus())) {
            throw new IllegalArgumentException("任务正在执行，请稍后刷新状态");
        }

        switch (taskName + "::" + triggerName) {
            case "asset-daily-snapshot::scheduled-00:00", "asset-daily-snapshot::scheduled-00:10" ->
                scheduledTaskRunService.run(taskName, triggerName, () ->
                    "savedCount=" + assetSnapshotService.captureDailySnapshots(today.minusDays(1)));
            case "renewal-auto-deduct::scheduled-08:00" -> runDueRenewals(taskName, triggerName, today);
            case "salary-settlement::scheduled-08:10" ->
                scheduledTaskRunService.run(taskName, triggerName, () -> {
                    salaryService.settleDueRecordsForAllUsers();
                    return "completed";
                });
            case "investment-auto-invest::scheduled-09:05" ->
                scheduledTaskRunService.run(taskName, triggerName, () -> {
                    int executedCount = investmentService.executeDueAutoInvestPlans();
                    int settledCount = investmentService.settlePendingFundTrades();
                    return "executedCount=" + executedCount + ", settledCount=" + settledCount;
                });
            case "investment-trade-settlement::scheduled-09:06" ->
                scheduledTaskRunService.run(taskName, triggerName, () ->
                    "settledCount=" + investmentService.settlePendingFundTrades());
            case "gold-price-cache-refresh::fixed-delay" ->
                scheduledTaskRunService.run(taskName, triggerName, () -> {
                    try {
                        goldPriceService.refreshCache();
                        return "completed";
                    } catch (Exception ex) {
                        throw new IllegalStateException("黄金价格缓存刷新失败", ex);
                    }
                });
            case "a-share-stock-screen::scheduled-16:10" ->
                scheduledTaskRunService.run(taskName, triggerName, () ->
                    String.valueOf(stockScreenerService.runScheduledScan(triggerName)));
            case "investment-night-sync::scheduled-21:30" ->
                scheduledTaskRunService.run(taskName, triggerName, () ->
                    String.valueOf(investmentService.runNightlyInvestmentSyncCycle(triggerName)));
            default -> throw new IllegalArgumentException("不支持手动执行该任务");
        }

        return getTodayTaskStatuses().stream()
            .filter(task -> taskName.equals(task.getTaskName()) && triggerName.equals(task.getTriggerName()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("任务执行状态读取失败"));
    }

    private ScheduledTaskStatusResponse resolveRetryTask(
        LocalDate today,
        ScheduledTaskRunEntity assetSnapshotRun,
        Map<String, ScheduledTaskRunEntity> latestRuns
    ) {
        ScheduledTaskStatusResponse response = resolveTask(
            "asset-daily-snapshot",
            "资产快照补偿",
            "scheduled-00:10",
            "00:10（补偿）",
            today,
            LocalTime.of(0, 10),
            latestRuns,
            false
        );
        if (assetSnapshotRun != null && "success".equals(assetSnapshotRun.getStatus()) && response.getStartedAt() == null) {
            applyStatus(response, "skipped", "已跳过", "主任务已成功，无需补偿", null);
        }
        return response;
    }

    private ScheduledTaskStatusResponse resolveGoldPriceTask(
        LocalDate today,
        Map<String, ScheduledTaskRunEntity> latestRuns
    ) {
        ScheduledTaskStatusResponse response = resolveTask(
            "gold-price-cache-refresh",
            "黄金价格缓存刷新",
            "fixed-delay",
            "每5分钟",
            today,
            LocalTime.MIDNIGHT,
            latestRuns,
            false
        );
        if (response.getStartedAt() == null) {
            applyStatus(response, "pending", "等待执行", "等待首次刷新", null);
        }
        return response;
    }

    private ScheduledTaskStatusResponse resolveTask(
        String taskName,
        String taskLabel,
        String triggerName,
        String scheduleLabel,
        LocalDate today,
        LocalTime scheduledTime,
        Map<String, ScheduledTaskRunEntity> latestRuns,
        boolean tradingDayOnly
    ) {
        ScheduledTaskStatusResponse response = new ScheduledTaskStatusResponse();
        response.setTaskName(taskName);
        response.setTaskLabel(taskLabel);
        response.setTriggerName(triggerName);
        response.setScheduleLabel(scheduleLabel);
        response.setTaskDate(today);

        ScheduledTaskRunEntity run = latestRuns.get(taskName + "::" + triggerName);
        response.setExecutable(
            scheduledTime != null
                && !LocalDateTime.now(SHANGHAI_ZONE_ID).isBefore(today.atTime(scheduledTime))
                && !(tradingDayOnly && isMarketClosed(today))
                && (run == null || !"running".equals(run.getStatus()))
        );
        if (run != null) {
            response.setStartedAt(run.getStartedAt());
            response.setFinishedAt(run.getFinishedAt());
            response.setResultMessage(run.getResultMessage());
            response.setErrorMessage(run.getErrorMessage());
            applyStatus(response, run.getStatus(), resolveStatusLabel(run.getStatus()), run.getResultMessage(), run.getErrorMessage());
            return response;
        }

        if (tradingDayOnly && isMarketClosed(today)) {
            applyStatus(response, "skipped", "已跳过", "今日为休市日", null);
            return response;
        }

        if (scheduledTime == null || LocalDateTime.now(SHANGHAI_ZONE_ID).isBefore(today.atTime(scheduledTime))) {
            applyStatus(response, "pending", "等待执行", "尚未到计划执行时间", null);
        } else {
            applyStatus(response, "missed", "未执行", "已过计划执行时间，未发现运行记录", null);
        }
        return response;
    }

    private void applyStatus(
        ScheduledTaskStatusResponse response,
        String status,
        String statusLabel,
        String resultMessage,
        String errorMessage
    ) {
        response.setStatus(status);
        response.setStatusLabel(statusLabel);
        if (response.getResultMessage() == null) {
            response.setResultMessage(resultMessage);
        }
        if (response.getErrorMessage() == null) {
            response.setErrorMessage(errorMessage);
        }
    }

    private ScheduledTaskRunEntity latest(ScheduledTaskRunEntity left, ScheduledTaskRunEntity right) {
        return Comparator.comparing(ScheduledTaskRunEntity::getStartedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .compare(left, right) >= 0 ? left : right;
    }

    private String resolveStatusLabel(String status) {
        return switch (status) {
            case "success" -> "已完成";
            case "running" -> "执行中";
            case "failed" -> "执行失败";
            default -> "未知状态";
        };
    }

    private boolean isMarketClosed(LocalDate date) {
        return date.getDayOfWeek().getValue() >= 6 || marketClosedDates.contains(date.toString());
    }

    private void runDueRenewals(String taskName, String triggerName, LocalDate today) {
        scheduledTaskRunService.run(taskName, triggerName, () -> {
            List<Long> subscriptionIds = renewalSubscriptionService.listDueSubscriptionIds(today);
            int successCount = 0;
            int failedCount = 0;
            for (Long subscriptionId : subscriptionIds) {
                try {
                    renewalSubscriptionService.processDueSubscription(subscriptionId);
                    successCount++;
                } catch (Exception ex) {
                    failedCount++;
                }
            }
            return "dueCount=" + subscriptionIds.size() + ", successCount=" + successCount + ", failedCount=" + failedCount;
        });
    }

    private LocalTime resolveScheduledTime(String taskName, String triggerName) {
        return switch (taskName + "::" + triggerName) {
            case "asset-daily-snapshot::scheduled-00:00", "gold-price-cache-refresh::fixed-delay" -> LocalTime.MIDNIGHT;
            case "asset-daily-snapshot::scheduled-00:10" -> LocalTime.of(0, 10);
            case "renewal-auto-deduct::scheduled-08:00" -> LocalTime.of(8, 0);
            case "salary-settlement::scheduled-08:10" -> LocalTime.of(8, 10);
            case "investment-auto-invest::scheduled-09:05" -> LocalTime.of(9, 5);
            case "investment-trade-settlement::scheduled-09:06" -> LocalTime.of(9, 6);
            case "a-share-stock-screen::scheduled-16:10" -> LocalTime.of(16, 10);
            case "investment-night-sync::scheduled-21:30" -> LocalTime.of(21, 30);
            default -> null;
        };
    }
}
