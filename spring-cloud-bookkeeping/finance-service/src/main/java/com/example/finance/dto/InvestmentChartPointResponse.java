package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvestmentChartPointResponse {
    private String label;
    private BigDecimal value;
    private BigDecimal open;
    private BigDecimal close;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal volume;
}
