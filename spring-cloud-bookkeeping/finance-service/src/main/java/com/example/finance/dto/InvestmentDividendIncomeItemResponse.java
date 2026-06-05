package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvestmentDividendIncomeItemResponse {
    private Long productId;
    private String productName;
    private String productSymbol;
    private String productType;
    private String unitName;
    private BigDecimal holdingQuantity;
    private BigDecimal marketValue;
    private BigDecimal costAmount;
    private BigDecimal estimatedDividendAmount;
    private BigDecimal estimatedDividendRate;
    private BigDecimal actualDividendAmount;
    private BigDecimal actualDividendRate;
}
