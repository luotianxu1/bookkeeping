package com.example.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.app.entity.ScheduledTaskRunEntity;
import com.example.app.mapper.ScheduledTaskRunMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class ScheduledTaskRunService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskRunService.class);
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final ScheduledTaskRunMapper scheduledTaskRunMapper;
    /** 同一任务（taskName::triggerName）在 JVM 内互斥，防止并发手动触发重复执行 */
    private final ConcurrentHashMap<String, Object> taskLocks = new ConcurrentHashMap<>();

    public ScheduledTaskRunService(ScheduledTaskRunMapper scheduledTaskRunMapper) {
        this.scheduledTaskRunMapper = scheduledTaskRunMapper;
    }

    public void run(String taskName, String triggerName, Supplier<String> action) {
        String lockKey = trim(taskName) + "::" + trim(triggerName);
        Object lock = taskLocks.computeIfAbsent(lockKey, key -> new Object());
        synchronized (lock) {
            if (hasRecentActiveRun(taskName, triggerName)) {
                throw new IllegalStateException("任务正在执行，请稍后刷新状态");
            }
            ScheduledTaskRunEntity run = start(taskName, triggerName);
            try {
                String resultMessage = action.get();
                finish(run, STATUS_SUCCESS, resultMessage, null);
            } catch (Exception ex) {
                finish(run, STATUS_FAILED, null, ex.getMessage());
                throw ex;
            }
        }
    }

    /** 存在 30 分钟内启动且未结束的运行记录时视为仍在执行（超时视为异常残留，不阻塞后续执行） */
    private boolean hasRecentActiveRun(String taskName, String triggerName) {
        Long count = scheduledTaskRunMapper.selectCount(new LambdaQueryWrapper<ScheduledTaskRunEntity>()
            .eq(ScheduledTaskRunEntity::getTaskName, trim(taskName))
            .eq(ScheduledTaskRunEntity::getTriggerName, trim(triggerName))
            .eq(ScheduledTaskRunEntity::getStatus, STATUS_RUNNING)
            .isNull(ScheduledTaskRunEntity::getFinishedAt)
            .ge(ScheduledTaskRunEntity::getStartedAt, LocalDateTime.now().minusMinutes(30)));
        return count != null && count > 0;
    }

    public boolean hasSuccessfulRunBetween(
        String taskName,
        String triggerName,
        LocalDateTime startedAtFrom,
        LocalDateTime startedAtTo
    ) {
        if (startedAtFrom == null || startedAtTo == null || !startedAtFrom.isBefore(startedAtTo)) {
            return false;
        }
        Long count = scheduledTaskRunMapper.selectCount(new LambdaQueryWrapper<ScheduledTaskRunEntity>()
            .eq(ScheduledTaskRunEntity::getTaskName, trim(taskName))
            .eq(ScheduledTaskRunEntity::getTriggerName, trim(triggerName))
            .eq(ScheduledTaskRunEntity::getStatus, STATUS_SUCCESS)
            .ge(ScheduledTaskRunEntity::getStartedAt, startedAtFrom)
            .lt(ScheduledTaskRunEntity::getStartedAt, startedAtTo));
        return count != null && count > 0;
    }

    private ScheduledTaskRunEntity start(String taskName, String triggerName) {
        ScheduledTaskRunEntity run = new ScheduledTaskRunEntity();
        run.setTaskName(trim(taskName));
        run.setTriggerName(trim(triggerName));
        run.setStatus(STATUS_RUNNING);
        run.setStartedAt(LocalDateTime.now());
        try {
            scheduledTaskRunMapper.insert(run);
            return run;
        } catch (Exception ex) {
            log.warn("定时任务审计开始记录失败：taskName={}, triggerName={}, reason={}",
                taskName, triggerName, ex.getMessage());
            return null;
        }
    }

    private void finish(ScheduledTaskRunEntity run, String status, String resultMessage, String errorMessage) {
        if (run == null || run.getId() == null) {
            return;
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        run.setStatus(status);
        run.setFinishedAt(finishedAt);
        run.setDurationMillis(Duration.between(run.getStartedAt(), finishedAt).toMillis());
        run.setResultMessage(trim(resultMessage));
        run.setErrorMessage(trim(errorMessage));
        try {
            scheduledTaskRunMapper.updateById(run);
        } catch (Exception ex) {
            log.warn("定时任务审计结束记录失败：taskName={}, triggerName={}, status={}, reason={}",
                run.getTaskName(), run.getTriggerName(), status, ex.getMessage());
        }
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_MESSAGE_LENGTH ? trimmed : trimmed.substring(0, MAX_MESSAGE_LENGTH);
    }
}
