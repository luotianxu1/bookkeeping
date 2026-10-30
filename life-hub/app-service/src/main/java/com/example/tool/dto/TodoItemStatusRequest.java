package com.example.tool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TodoItemStatusRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "状态不能为空")
    private String status;
}
