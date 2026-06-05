package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvestmentDividendIncomeSummaryResponse {
    private BigDecimal estimatedDividendAmount;
    private BigDecimal estimatedDividendRate;
    private BigDecimal actualDividendAmount;
    private BigDecimal actualDividendRate;
    private Integer holdingCount;
}
