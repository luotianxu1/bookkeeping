package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvestmentTrendAllocationResponse {
    private String productType;
    private String label;
    private BigDecimal marketValue;
    private BigDecimal percent;
}
