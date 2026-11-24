package com.example.app.dto;

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
    /** 已实现收益（卖出落袋+分红），累计收益 = 已实现 + 持仓浮盈 */
    private BigDecimal realizedProfit;
    /** 累计投入成本，买入累加、卖出不扣减；累计收益率分母 */
    private BigDecimal investedAmount;
    private Boolean includeInNetWorth;
    private String status;
    private LocalDateTime lastSyncedAt;
    /** 最新行情/净值快照的归属日期（净值即 FSRQ），用于前端展示"净值更新于"而非同步时间 */
    private LocalDate quoteDate;
    /** 最新行情快照的落库时间，即该净值在本系统可用的时刻 */
    private LocalDateTime quoteTime;
    private Boolean hasRecentDividendPlan;
    private String recentDividendStatus;
    private LocalDate recentDividendDate;
    private BigDecimal recentDividendPerUnit;
    private String subscriptionStatus;
    private LocalDate subscriptionAppliedDate;
    private LocalDate subscriptionExpectedConfirmDate;
    private LocalDateTime subscriptionConfirmedAt;
    private String dividendType;
    private Long dividendFundingAccountId;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
