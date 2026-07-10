package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TransactionPageResponse {
    private List<TransactionResponse> items;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;
    private BigDecimal incomeTotal;
    private BigDecimal expenseTotal;
    private BigDecimal balanceTotal;
}
