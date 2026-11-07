package com.example.app.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Data
public class ExpenseRecoveryResponse {
    private Long id;
    private String title;
    private String categoryName;
    private String accountName;
    private String currencyCode;
    private LocalDateTime occurredAt;
    private BigDecimal amount;
    private BigDecimal reimbursementAmount;
    private String reimbursementRecipient;
    private LocalDate expectedReimbursementDate;
    private BigDecimal refundedAmount;
    private BigDecimal reimbursedAmount;
    private BigDecimal recoverableAmount;
    private BigDecimal pendingAmount;
    private String reimbursementStatus;
    private List<Receipt> receipts;
    public record Receipt(Long id, String kind, BigDecimal amount, Long accountId, String accountName,
        LocalDateTime occurredAt, String remark) {}
    public record PendingSummary(String currencyCode, BigDecimal amount, long count, BigDecimal overdueAmount) {}
    public record Page(List<ExpenseRecoveryResponse> items, long total, int page, int totalPages, List<PendingSummary> pendingSummary) {}
}
