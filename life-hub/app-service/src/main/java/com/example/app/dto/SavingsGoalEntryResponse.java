package com.example.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SavingsGoalEntryResponse(Long id, Long goalId, String type, BigDecimal amount,
    LocalDate occurredOn, String remark, LocalDateTime createdAt, Long transactionId) {
    public record Page(List<SavingsGoalEntryResponse> entries, Long nextBeforeId) {}
}
