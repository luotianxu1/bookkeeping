package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.ScheduledTaskStatusResponse;
import com.example.finance.service.ScheduledTaskStatusService;
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

    private final ScheduledTaskStatusService scheduledTaskStatusService;

    public ScheduledTaskController(ScheduledTaskStatusService scheduledTaskStatusService) {
        this.scheduledTaskStatusService = scheduledTaskStatusService;
    }

    @GetMapping("/today")
    @Operation(summary = "查询当天定时任务状态")
    public Result<List<ScheduledTaskStatusResponse>> getTodayTaskStatuses() {
        return Result.ok(scheduledTaskStatusService.getTodayTaskStatuses());
    }

    @PostMapping("/today/{taskName}/{triggerName}/execute")
    @Operation(summary = "手动执行当天已到时间的定时任务")
    public Result<ScheduledTaskStatusResponse> executeTodayTask(
        @PathVariable("taskName") String taskName,
        @PathVariable("triggerName") String triggerName
    ) {
        return Result.ok(scheduledTaskStatusService.executeTodayTask(taskName, triggerName));
    }
}
