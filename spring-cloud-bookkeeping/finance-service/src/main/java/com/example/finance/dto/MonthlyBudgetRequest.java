package com.example.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MonthlyBudgetRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "预算月份不能为空")
    private LocalDate budgetMonth;

    @NotNull(message = "预算金额不能为空")
    @DecimalMin(value = "0.01", message = "预算金额必须大于0")
    private BigDecimal amount;

    @Size(max = 3, message = "币种编码不能超过3个字符")
    private String currencyCode;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
