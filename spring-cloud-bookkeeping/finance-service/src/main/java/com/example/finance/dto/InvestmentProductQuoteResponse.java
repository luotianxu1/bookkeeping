package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InvestmentProductQuoteResponse {
    private String productType;
    private String productTypeLabel;
    private String symbol;
    private String name;
    private String market;
    private String unitName;
    private BigDecimal latestPrice;
    private BigDecimal change;
    private BigDecimal changePercent;
    private LocalDate quoteDate;
    private String source;
}
