package com.example.app.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FundProfitPageSummaryMetricResponse {
    private String key;
    private String label;
    private BigDecimal profit;
    private BigDecimal profitRate;
}
