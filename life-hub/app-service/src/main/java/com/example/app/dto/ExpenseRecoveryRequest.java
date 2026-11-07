package com.example.app.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExpenseRecoveryRequest(
    @NotNull Long transactionId,
    @NotNull Long accountId,
    @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount,
    @NotNull @Pattern(regexp = "refund|reimbursement") String kind,
    @NotNull LocalDateTime occurredAt,
    @Size(max = 500) String remark,
    @Pattern(regexp = "[A-Za-z0-9_-]{16,64}") String requestId
) {}
