package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SalaryMonthPageResponse {

    private Integer year;
    private BigDecimal defaultMonthlyGrossSalary;
    private Integer recordedMonths;
    private BigDecimal recordedGrossIncome;
    private BigDecimal estimatedAnnualGrossIncome;
    private List<MetricItem> metrics;
    private List<RecordItem> records;
    private LocalDateTime updatedAt;

    @Data
    public static class MetricItem {
        private String label;
        private BigDecimal value;
    }

    @Data
    public static class RecordItem {
        private Long id;
        private String monthKey;
        private String monthLabel;
        private BigDecimal grossSalary;
        private String note;
        private boolean editable;
    }
}
