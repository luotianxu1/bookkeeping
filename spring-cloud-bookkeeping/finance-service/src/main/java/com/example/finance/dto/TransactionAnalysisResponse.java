package com.example.finance.dto;

import lombok.Data;

import java.util.List;

@Data
public class TransactionAnalysisResponse {

    private Long userId;
    private String period;
    private String month;
    private Integer year;
    private TransactionAnalysisSummaryResponse summary;
    private List<TransactionAnalysisCategoryBreakdownResponse> incomeBreakdown;
    private List<TransactionAnalysisCategoryBreakdownResponse> expenseBreakdown;
    private List<TransactionAnalysisTrendPointResponse> trendPoints;
    private List<TransactionAnalysisPeriodSummaryResponse> periodSummaries;
}
