package com.example.finance.service;

import com.example.finance.entity.ScheduledTaskRunEntity;
import com.example.finance.mapper.ScheduledTaskRunMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;

@Service
public class ScheduledTaskRunService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskRunService.class);
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final ScheduledTaskRunMapper scheduledTaskRunMapper;

    public ScheduledTaskRunService(ScheduledTaskRunMapper scheduledTaskRunMapper) {
        this.scheduledTaskRunMapper = scheduledTaskRunMapper;
    }

    public void run(String taskName, String triggerName, Supplier<String> action) {
        ScheduledTaskRunEntity run = start(taskName, triggerName);
        try {
            String resultMessage = action.get();
            finish(run, STATUS_SUCCESS, resultMessage, null);
        } catch (Exception ex) {
            finish(run, STATUS_FAILED, null, ex.getMessage());
            throw ex;
        }
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
