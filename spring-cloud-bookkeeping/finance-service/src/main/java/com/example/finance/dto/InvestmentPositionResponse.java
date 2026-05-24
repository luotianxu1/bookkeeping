package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InvestmentPositionResponse {
    private Long id;
    private Long userId;
    private Long accountId;
    private String accountName;
    private Long productId;
    private String productType;
    private String productName;
    private String productSymbol;
    private String market;
    private String unitName;
    private String currencyCode;
    private BigDecimal holdingQuantity;
    private BigDecimal availableQuantity;
    private BigDecimal frozenQuantity;
    private BigDecimal costAmount;
    private BigDecimal avgCostPrice;
    private BigDecimal currentPrice;
    private BigDecimal marketValue;
    private BigDecimal dayProfit;
    private BigDecimal dayProfitRate;
    private BigDecimal holdingProfit;
    private BigDecimal holdingProfitRate;
    private BigDecimal cumulativeProfit;
    private BigDecimal cumulativeProfitRate;
    private Boolean includeInNetWorth;
    private String status;
    private LocalDateTime lastSyncedAt;
    private String subscriptionStatus;
    private LocalDate subscriptionAppliedDate;
    private LocalDate subscriptionExpectedConfirmDate;
    private LocalDateTime subscriptionConfirmedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
