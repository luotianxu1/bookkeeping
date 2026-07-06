package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SalaryOverviewResponse {

    private String monthKey;
    private Integer payDay;
    private Integer paidMonths;
    private BigDecimal grossIncome;
    private BigDecimal netIncome;
    private BigDecimal totalDeduction;
    private BigDecimal taxAmount;
    private BigDecimal annualIncome;
    private BigDecimal netRate;
    private List<MetricItem> metrics;
    private List<DetailItem> details;
    private List<AccountSummary> linkedAccounts;
    private TaxSummary taxSummary;

    @Data
    public static class MetricItem {
        private String label;
        private BigDecimal value;
    }

    @Data
    public static class DetailItem {
        private String label;
        private BigDecimal value;
        private String detail;
    }

    @Data
    public static class AccountSummary {
        private String accountType;
        private String title;
        private BigDecimal currentBalance;
        private BigDecimal monthlyDeposit;
        private String routePath;
    }

    @Data
    public static class TaxSummary {
        private BigDecimal currentMonthTax;
        private BigDecimal annualTax;
        private BigDecimal annualIncome;
        private String routePath;
    }
}
