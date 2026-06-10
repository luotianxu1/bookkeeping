package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvestmentDividendForecastResponse {
    private String productType;
    private String productTypeLabel;
    private String symbol;
    private String name;
    private String market;
    private String unitName;
    private BigDecimal currentPrice;
    private Integer basisYear;
    private Integer lastYearDividendCount;
    private BigDecimal lastYearDividendPerUnit;
    private BigDecimal estimatedHoldingQuantity;
    private BigDecimal estimatedHoldingAmount;
    private BigDecimal estimatedDividendAmount;
    private BigDecimal estimatedDividendRate;
    private String calculationNote;
    private String source;
}
