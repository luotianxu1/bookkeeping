package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FundProfitSelectionResponse {
    private String key;
    private String label;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate comparisonDate;
    private BigDecimal profit;
    private BigDecimal profitRate;
    private Integer positiveFundCount;
    private Integer negativeFundCount;
}
