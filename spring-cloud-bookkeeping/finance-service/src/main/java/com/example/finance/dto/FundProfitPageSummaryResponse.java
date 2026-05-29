package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class FundProfitPageSummaryResponse {
    private BigDecimal holdingAmount;
    private BigDecimal investedAmount;
    private BigDecimal totalProfit;
    private BigDecimal totalProfitRate;
    private Integer fundCount;
    private LocalDateTime lastSyncedAt;
    private String activeShortcut;
    private List<FundProfitPageSummaryMetricResponse> shortcuts;
}
