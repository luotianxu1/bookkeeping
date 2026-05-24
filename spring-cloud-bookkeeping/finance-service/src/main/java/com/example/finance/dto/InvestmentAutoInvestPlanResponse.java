package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InvestmentAutoInvestPlanResponse {

    private Long id;
    private Long userId;
    private Long accountId;
    private String accountName;
    private Long positionId;
    private Long productId;
    private String productName;
    private String productSymbol;
    private Long fundingAccountId;
    private String fundingAccountName;
    private String frequency;
    private BigDecimal amount;
    private String currencyCode;
    private LocalDate nextExecuteDate;
    private LocalDateTime lastExecutedAt;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
