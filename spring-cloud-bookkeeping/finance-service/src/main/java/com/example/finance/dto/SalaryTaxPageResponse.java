package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SalaryTaxPageResponse {

    private Integer year;
    private Integer paidMonths;
    private BigDecimal annualIncome;
    private BigDecimal annualTax;
    private BigDecimal currentMonthTax;
    private BigDecimal annualNetIncome;
    private BigDecimal monthlyAverageNetIncome;
    private BigDecimal specialDeductionTotal;
    private List<MetricItem> metrics;
    private List<DeductionItem> deductions;
    private List<MonthTaxItem> monthItems;

    @Data
    public static class MetricItem {
        private String label;
        private BigDecimal value;
    }

    @Data
    public static class DeductionItem {
        private String label;
        private BigDecimal monthlyValue;
        private BigDecimal annualValue;
    }

    @Data
    public static class MonthTaxItem {
        private String monthKey;
        private String monthLabel;
        private BigDecimal grossIncome;
        private BigDecimal taxAmount;
        private BigDecimal takeHomeIncome;
        private String statusText;
    }
}
