package com.example.tool.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class WeightRecordRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "记录日期不能为空")
    private LocalDate recordDate;

    @NotNull(message = "体重不能为空")
    @DecimalMin(value = "20", message = "体重需在20-300kg之间")
    @DecimalMax(value = "300", message = "体重需在20-300kg之间")
    private BigDecimal weight;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
