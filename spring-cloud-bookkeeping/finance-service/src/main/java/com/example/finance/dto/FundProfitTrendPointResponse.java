package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FundProfitTrendPointResponse {
    private String key;
    private String label;
    private LocalDate date;
    private BigDecimal profit;
}
