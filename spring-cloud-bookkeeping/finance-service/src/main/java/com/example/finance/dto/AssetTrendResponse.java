package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AssetTrendResponse {
    private Long userId;
    private Long accountId;
    private String range;
    private String rangeLabel;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalAssets;
    private BigDecimal cumulativeProfit;
    private BigDecimal cumulativeProfitRate;
    private BigDecimal periodChangeAmount;
    private BigDecimal periodChangeRate;
    private LocalDateTime lastSyncedAt;
    private List<AssetTrendPointResponse> trendPoints;
    private List<AssetTrendAllocationResponse> allocations;
    private List<AssetTrendContributorResponse> contributors;
}
