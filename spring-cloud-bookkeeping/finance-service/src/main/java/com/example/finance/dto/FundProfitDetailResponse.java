package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FundProfitDetailResponse {
    private Long positionId;
    private Long productId;
    private String productName;
    private String productSymbol;
    private String accountName;
    private BigDecimal holdingQuantity;
    private BigDecimal netValue;
    private BigDecimal holdingAmount;
    private BigDecimal costAmount;
    private BigDecimal periodProfit;
    private BigDecimal periodProfitRate;
}
