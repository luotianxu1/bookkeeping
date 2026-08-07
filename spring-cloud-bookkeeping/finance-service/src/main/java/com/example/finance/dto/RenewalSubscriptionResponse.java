package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RenewalSubscriptionResponse {

    private Long id;
    private Long userId;
    private String name;
    private String providerName;
    private BigDecimal amount;
    private String currencyCode;
    private Long fundingAccountId;
    private String fundingAccountName;
    private Long categoryId;
    private String categoryName;
    private String categoryIcon;
    private String categoryColor;
    private Integer billingDay;
    private String billingCycle;
    private LocalDate nextBillingDate;
    private LocalDateTime lastChargedAt;
    private Long lastTransactionId;
    private String lastChargeStatus;
    private String lastChargeMessage;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
