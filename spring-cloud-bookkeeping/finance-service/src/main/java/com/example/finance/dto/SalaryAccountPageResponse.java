package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SalaryAccountPageResponse {

    private String accountType;
    private String title;
    private String subtitle;
    private String badgeText;
    private Integer year;
    private BigDecimal currentBalance;
    private BigDecimal initialBalance;
    private BigDecimal monthlyPersonal;
    private BigDecimal monthlyCompany;
    private BigDecimal yearlyIncrease;
    private List<MetricItem> metrics;
    private List<DetailItem> details;
    private List<RecordItem> records;
    private Forecast forecast;
    private LocalDateTime updatedAt;

    @Data
    public static class MetricItem {
        private String label;
        private BigDecimal value;
    }

    @Data
    public static class DetailItem {
        private String label;
        private String description;
        private BigDecimal value;
    }

    @Data
    public static class RecordItem {
        private Long id;
        private String monthKey;
        private String monthLabel;
        private String recordType;
        private String pillText;
        private String amountLabel;
        private BigDecimal amountValue;
        private String balanceLabel;
        private BigDecimal balanceValue;
        private String note;
        private boolean editable;
    }

    @Data
    public static class Forecast {
        private Integer sourceYear;
        private Integer forecastYear;
        private BigDecimal sourceAnnualGrossIncome;
        private BigDecimal predictedMonthlyBase;
        private BigDecimal predictedPersonal;
        private BigDecimal predictedCompany;
    }
}
