package com.example.app.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
public record CategoryBudgetResponse(Long id, LocalDate budgetMonth, Long categoryId, String categoryName,
    BigDecimal amount, BigDecimal usedAmount, BigDecimal remainingAmount, BigDecimal usagePercent, String currencyCode) {}
