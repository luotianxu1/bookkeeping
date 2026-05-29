package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FundProfitCalendarCellResponse {
    private String key;
    private String label;
    private String secondaryLabel;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal profit;
    private BigDecimal profitRate;
    private Boolean selected;
    private Boolean current;
}
