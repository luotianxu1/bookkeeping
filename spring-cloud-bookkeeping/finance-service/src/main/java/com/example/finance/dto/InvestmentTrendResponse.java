package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InvestmentTrendResponse {
    private Long userId;
    private Long accountId;
    private String range;
    private String rangeLabel;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalMarketValue;
    private BigDecimal cumulativeProfit;
    private BigDecimal cumulativeProfitRate;
    private BigDecimal periodChangeAmount;
    private BigDecimal periodChangeRate;
    private LocalDateTime lastSyncedAt;
    private List<InvestmentTrendPointResponse> trendPoints;
    private List<InvestmentTrendAllocationResponse> allocations;
    private List<InvestmentTrendContributorResponse> contributors;
}
