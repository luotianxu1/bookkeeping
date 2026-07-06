package com.example.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SalaryInitialBalanceRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "初始金额不能为空")
    @DecimalMin(value = "0.00", message = "初始金额不能小于0")
    private BigDecimal amount;

    @NotNull(message = "生效月份不能为空")
    private LocalDate recordMonth;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String note;
}
