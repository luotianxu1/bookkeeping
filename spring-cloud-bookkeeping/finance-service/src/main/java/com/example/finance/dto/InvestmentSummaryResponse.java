package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InvestmentSummaryResponse {
    private Long userId;
    private BigDecimal totalMarketValue;
    private BigDecimal dayProfit;
    private BigDecimal dayProfitRate;
    private BigDecimal holdingProfit;
    private BigDecimal holdingProfitRate;
    private BigDecimal cumulativeProfit;
    private BigDecimal cumulativeProfitRate;
    private LocalDateTime lastSyncedAt;
}
