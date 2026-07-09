package com.example.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scheduled_task_runs")
public class ScheduledTaskRunEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_name")
    private String taskName;

    @TableField("trigger_name")
    private String triggerName;

    private String status;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("duration_millis")
    private Long durationMillis;

    @TableField("result_message")
    private String resultMessage;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
