package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LiabilityRecordResponse {

    private Long id;
    private Long userId;
    private Long accountId;
    private String accountName;
    private BigDecimal amount;
    private Integer installmentTotalPeriods;
    private Integer installmentCurrentPeriod;
    private String repaymentStatus;
    private String repaymentType;
    private LocalDateTime paidAt;
    private String currencyCode;
    private String remark;
    private LocalDateTime occurredAt;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
