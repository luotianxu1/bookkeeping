package com.example.tool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TodoItemRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "事项标题不能为空")
    @Size(max = 120, message = "事项标题不能超过120个字符")
    private String title;

    @NotNull(message = "截止时间不能为空")
    private LocalDateTime dueAt;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    private Integer sortOrder;
    private String status;
}
