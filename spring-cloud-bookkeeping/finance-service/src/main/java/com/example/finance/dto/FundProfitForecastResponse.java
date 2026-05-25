package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class FundProfitForecastResponse {
    private Long userId;
    private BigDecimal holdingAmount;
    private BigDecimal estimateProfit;
    private BigDecimal estimateProfitRate;
    private BigDecimal totalProfit;
    private BigDecimal totalProfitRate;
    private Integer fundCount;
    private LocalDateTime estimatedAt;
    private List<FundProfitForecastAccountResponse> accounts;
    private List<FundProfitForecastHoldingResponse> holdings;
}
