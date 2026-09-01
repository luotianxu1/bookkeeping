package com.example.finance.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ScheduledTaskStatusResponse {
    private String taskName;
    private String taskLabel;
    private String triggerName;
    private String scheduleLabel;
    private String status;
    private String statusLabel;
    private Boolean executable;
    private LocalDate taskDate;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String resultMessage;
    private String errorMessage;
}
