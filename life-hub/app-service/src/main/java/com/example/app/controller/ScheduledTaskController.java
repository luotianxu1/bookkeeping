package com.example.app.controller;

import com.example.common.result.Result;
import com.example.app.dto.ScheduledTaskStatusResponse;
import com.example.app.service.ScheduledTaskStatusService;
import com.example.app.service.AdminAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/finance/scheduled-tasks")
@Tag(name = "任务管理", description = "当天定时任务计划与执行状态")
public class ScheduledTaskController {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskController.class);
    private final ScheduledTaskStatusService scheduledTaskStatusService;
    private final AdminAccessService adminAccess;

    public ScheduledTaskController(ScheduledTaskStatusService scheduledTaskStatusService, AdminAccessService adminAccess) {
        this.scheduledTaskStatusService = scheduledTaskStatusService;
        this.adminAccess = adminAccess;
    }

    @GetMapping("/today")
    @Operation(summary = "查询当天定时任务状态")
    public Result<List<ScheduledTaskStatusResponse>> getTodayTaskStatuses() {
        adminAccess.requireAdmin();
        return Result.ok(scheduledTaskStatusService.getTodayTaskStatuses());
    }

    @PostMapping("/today/{taskName}/{triggerName}/execute")
    @Operation(summary = "手动执行当天已到时间的定时任务")
    public Result<ScheduledTaskStatusResponse> executeTodayTask(
        @PathVariable("taskName") String taskName,
        @PathVariable("triggerName") String triggerName
    ) {
        long actorId = adminAccess.requireAdmin();
        log.info("管理员手动执行定时任务: userId={}, taskName={}, triggerName={}", actorId, taskName, triggerName);
        return Result.ok(scheduledTaskStatusService.executeTodayTask(taskName, triggerName));
    }
}
