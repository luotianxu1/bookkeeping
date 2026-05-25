package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FundProfitForecastAccountResponse {
    private Long accountId;
    private String accountName;
    private BigDecimal holdingAmount;
    private BigDecimal estimateProfit;
    private BigDecimal estimateProfitRate;
    private BigDecimal totalProfit;
    private BigDecimal totalProfitRate;
    private Integer fundCount;
    private LocalDateTime estimatedAt;
}
