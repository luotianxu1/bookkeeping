package com.example.app.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class TermDepositDtos {
    private TermDepositDtos() {}
    public record Create(Long accountId, Long fundingAccountId, Long payoutAccountId, String name,
        BigDecimal principal, BigDecimal annualRate, LocalDate startDate, LocalDate maturityDate,
        Integer termMonths, String maturityAction, String openingMode, String remark,
        List<SavingsAllocation> savingsAllocations, String requestId) {}
    public record Edit(String name, Long payoutAccountId, BigDecimal annualRate, LocalDate startDate,
        LocalDate maturityDate, Integer termMonths, String maturityAction, String remark, String requestId) {}
    public record Settle(String action, Long payoutAccountId, Long interestCategoryId, BigDecimal actualInterest,
        LocalDate occurredOn, BigDecimal renewalAnnualRate, LocalDate renewalMaturityDate,
        Integer renewalTermMonths, String remark, String requestId) {}
    public record Reverse(Long operationId, String requestId) {}
    public record Cancel(String requestId) {}
    public record View(Long id, Long accountId, String accountName, Long fundingAccountId, Long payoutAccountId,
        Long parentDepositId, String name, String currencyCode, BigDecimal principal, BigDecimal annualRate,
        LocalDate startDate, LocalDate maturityDate, Integer termMonths, String maturityAction, String openingMode,
        String status, String remark, BigDecimal expectedInterest, BigDecimal expectedTotal, long daysRemaining,
        LocalDate asOfDate, boolean canCancel) {}
    public record Operation(Long id, String operation, Long cashAccountId, String cashAccountName, Long childDepositId,
        BigDecimal principal, BigDecimal interest, BigDecimal cashDelta, LocalDate occurredOn,
        String detail, LocalDateTime reversedAt, LocalDateTime createdAt) {}
    public record Detail(View deposit, List<Operation> operations) {}
}
