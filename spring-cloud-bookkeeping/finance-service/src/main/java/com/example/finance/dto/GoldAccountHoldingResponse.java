package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GoldAccountHoldingResponse {
    private Long id;
    private Long accountId;
    private String accountName;
    private Long positionId;
    private Long productId;
    private String productName;
    private String productSymbol;
    private BigDecimal currentPrice;
    private BigDecimal purchaseAmount;
    private BigDecimal weight;
    private BigDecimal holdingProfit;
    private BigDecimal marketValue;
    private BigDecimal avgCostPrice;
    private LocalDateTime createdAt;
}
