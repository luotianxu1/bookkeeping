package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FreeEstimateResponse {
    private Long userId;
    private LocalDate asOfDate;
    private BigDecimal currentNetAssets;
    private BigDecimal historicalMonthlyIncome;
    private BigDecimal historicalMonthlyExpense;
    private BigDecimal budgetMonthlyExpense;
    private BigDecimal recurringMonthlyExpense;
    private BigDecimal monthlyExpense;
    private BigDecimal monthlyIncome;
    private BigDecimal monthlySurplus;
    private LocalDate dataStartDate;
    private LocalDate dataEndDate;
    private Integer dataMonths;
    private Integer incomeTransactionCount;
    private Integer expenseTransactionCount;
}
