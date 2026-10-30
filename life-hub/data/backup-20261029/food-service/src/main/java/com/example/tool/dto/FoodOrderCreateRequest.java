package com.example.tool.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class FoodOrderCreateRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Size(max = 120, message = "菜单标题不能超过120个字符")
    private String title;

    private LocalDate plannedFor;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    @NotEmpty(message = "请至少选择一道菜品")
    private List<Long> dishIds;
}
