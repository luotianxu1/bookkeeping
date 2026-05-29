package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionAnalysisSummaryResponse {

    private BigDecimal income;
    private BigDecimal expense;
    private BigDecimal surplus;
    private Integer incomeCount;
    private Integer expenseCount;
    private Integer transactionCount;
}
