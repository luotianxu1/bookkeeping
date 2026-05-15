package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MonthlyBudgetResponse {

    private Long id;
    private Long userId;
    private LocalDate budgetMonth;
    private BigDecimal amount;
    private String currencyCode;
    private BigDecimal usedAmount;
    private BigDecimal remainingAmount;
    private BigDecimal usagePercent;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
