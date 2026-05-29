package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvestmentTrendContributorResponse {
    private Long positionId;
    private Long productId;
    private String productType;
    private String productName;
    private String productSymbol;
    private BigDecimal contributionAmount;
    private BigDecimal contributionRate;
}
