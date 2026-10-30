package com.example.tool.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class HealthProfileRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "性别不能为空")
    @Min(value = 1, message = "性别取值不支持")
    @Max(value = 2, message = "性别取值不支持")
    private Integer gender;

    @NotNull(message = "年龄不能为空")
    @Min(value = 10, message = "年龄需在10-100岁之间")
    @Max(value = 100, message = "年龄需在10-100岁之间")
    private Integer age;

    @NotNull(message = "身高不能为空")
    @DecimalMin(value = "80", message = "身高需在80-250cm之间")
    @DecimalMax(value = "250", message = "身高需在80-250cm之间")
    private BigDecimal heightCm;
}
