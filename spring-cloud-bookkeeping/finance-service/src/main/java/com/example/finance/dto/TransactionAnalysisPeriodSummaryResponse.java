package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TransactionAnalysisPeriodSummaryResponse {

    private String key;
    private String label;
    private BigDecimal income;
    private BigDecimal expense;
    private BigDecimal surplus;
    private Integer transactionCount;
    private List<TransactionResponse> transactions;
}
