package com.example.tool.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TodoItemResponse {

    private Long id;
    private Long userId;
    private String title;
    private LocalDateTime dueAt;
    private String remark;
    private Integer sortOrder;
    private String status;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
