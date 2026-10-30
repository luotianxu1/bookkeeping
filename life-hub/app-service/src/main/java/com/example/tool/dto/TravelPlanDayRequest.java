package com.example.tool.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TravelPlanDayRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "天数不能为空")
    @Min(value = 1, message = "天数序号必须大于0")
    private Integer dayIndex;

    @Size(max = 80, message = "标题不能超过80个字符")
    private String title;

    private LocalDate travelDate;
    private Integer sortOrder;
}
