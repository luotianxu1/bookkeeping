package com.example.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SavingsGoalResponse(Long id, String name, Long accountId, String accountName,
    String currencyCode, BigDecimal targetAmount, LocalDate targetDate, String remark,
    boolean accountAvailable, BigDecimal currentAmount, BigDecimal remainingAmount,
    BigDecimal progressPercent, String status, Integer monthsRemaining, BigDecimal monthlyNeeded,
    LocalDate asOfDate, BigDecimal accountBalance, BigDecimal accountReservedAmount, BigDecimal accountAvailableAmount) {}
