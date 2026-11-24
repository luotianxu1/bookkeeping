package com.example.app.dto;

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
    /** 累计投入成本（买入累加、卖出不扣减），累计收益率分母 */
    private BigDecimal investedAmount;
    private LocalDateTime lastSyncedAt;
}
