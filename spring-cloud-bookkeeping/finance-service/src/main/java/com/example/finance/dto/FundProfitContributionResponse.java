package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FundProfitContributionResponse {
    private Long positionId;
    private Long productId;
    private String productName;
    private String productSymbol;
    private String accountName;
    private BigDecimal contributionAmount;
    private BigDecimal contributionRate;
    private BigDecimal holdingAmount;
    private BigDecimal holdingQuantity;
}
