package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionAnalysisCategoryBreakdownResponse {

    private Long categoryId;
    private String categoryName;
    private String categoryIcon;
    private String categoryColor;
    private BigDecimal amount;
    private BigDecimal percent;
    private Integer transactionCount;
}
