package com.example.app.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SavingsGoalEntryRequest(
    @NotNull @Pattern(regexp = "deposit|withdrawal", message = "请选择存入或取出") String type,
    @NotNull(message = "请输入金额") @DecimalMin(value = "0.01", message = "金额必须大于0")
    @Digits(integer = 16, fraction = 2, message = "金额最多两位小数") BigDecimal amount,
    @NotNull(message = "请选择日期") LocalDate occurredOn,
    @Size(max = 500, message = "备注最多500字") String remark,
    @Pattern(regexp = "[A-Za-z0-9_-]{16,64}", message = "请求 ID 格式无效") String requestId
) {}
