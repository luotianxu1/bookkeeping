package com.example.app.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SavingsGoalRequest(
    @NotBlank(message = "请输入目标名称") @Size(max = 80, message = "目标名称最多80字") String name,
    @NotNull(message = "请选择现金账户") @Positive Long accountId,
    @NotNull(message = "请输入目标金额") @DecimalMin(value = "0.01", message = "目标金额必须大于0")
    @Digits(integer = 16, fraction = 2, message = "目标金额最多两位小数") BigDecimal targetAmount,
    LocalDate targetDate,
    @Size(max = 500, message = "备注最多500字") String remark,
    @Pattern(regexp = "[A-Za-z0-9_-]{16,64}", message = "请求 ID 格式无效") String requestId
) {}
