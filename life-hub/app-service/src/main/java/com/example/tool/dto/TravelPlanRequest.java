package com.example.tool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TravelPlanRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "旅行名称不能为空")
    @Size(max = 80, message = "旅行名称不能超过80个字符")
    private String name;

    @Size(max = 120, message = "目的地不能超过120个字符")
    private String destination;

    private LocalDate startDate;
    private LocalDate endDate;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    private Integer sortOrder;
    private String status;
}
