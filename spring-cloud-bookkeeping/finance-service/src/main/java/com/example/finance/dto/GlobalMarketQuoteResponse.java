package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GlobalMarketQuoteResponse {
    private String symbol;
    private String name;
    private BigDecimal price;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal preClose;
    private String quoteDate;
    private String quoteTime;
    private String dataSource;
}
