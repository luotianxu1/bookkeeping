package com.example.app.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FundProfitForecastHoldingResponse {
    private Long accountId;
    private String accountName;
    private Long positionId;
    private Long productId;
    private String productName;
    private String productSymbol;
    private String unitName;
    private BigDecimal holdingQuantity;
    private BigDecimal costAmount;
    /** 累计投入成本（含已实现收益的分母口径） */
    private BigDecimal investedAmount;
    private BigDecimal holdingAmount;
    private BigDecimal estimateProfit;
    private BigDecimal estimateProfitRate;
    private BigDecimal totalProfit;
    private BigDecimal totalProfitRate;
    private BigDecimal estimatedNetValue;
    private BigDecimal officialNetValue;
    private LocalDateTime estimatedAt;
}
