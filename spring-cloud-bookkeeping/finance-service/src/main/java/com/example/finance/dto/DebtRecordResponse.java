package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DebtRecordResponse {

    private Long id;
    private Long userId;
    private Long accountId;
    private Long contactId;
    private String accountName;
    private Long fundingAccountId;
    private String fundingAccountName;
    private String direction;
    private BigDecimal amount;
    private String currencyCode;
    private String remark;
    private LocalDateTime occurredAt;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
