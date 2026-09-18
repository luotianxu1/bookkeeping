package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SalaryCashPageResponse {

    private Integer year;
    private Integer paidMonths;
    private BigDecimal annualCashIncome;
    private BigDecimal currentMonthCashIncome;
    private BigDecimal annualGrossIncome;
    private BigDecimal annualPersonalDeduction;
    private BigDecimal annualTax;
    private List<MetricItem> metrics;
    private List<MonthCashItem> monthItems;

    @Data
    public static class MetricItem {
        private String label;
        private BigDecimal value;
    }

    @Data
    public static class MonthCashItem {
        private String monthKey;
        private String monthLabel;
        private BigDecimal grossIncome;
        private BigDecimal personalDeduction;
        private BigDecimal taxAmount;
        private BigDecimal cashIncome;
        private String statusText;
    }
}
