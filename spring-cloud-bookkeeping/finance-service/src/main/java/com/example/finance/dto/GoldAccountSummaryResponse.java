package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GoldAccountSummaryResponse {
    private BigDecimal totalWeight;
    private BigDecimal averagePrice;
    private BigDecimal purchaseTotal;
    private BigDecimal estimatedValue;
    private BigDecimal estimatedProfit;
    private BigDecimal profitRate;
    private BigDecimal cumulativeProfit;
}
