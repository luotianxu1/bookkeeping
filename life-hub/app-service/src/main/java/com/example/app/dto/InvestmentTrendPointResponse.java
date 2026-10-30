package com.example.app.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvestmentTrendPointResponse {
    private String key;
    private String label;
    private BigDecimal value;
}
