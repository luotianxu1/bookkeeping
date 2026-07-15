package com.example.finance.dto;

import lombok.Data;

import java.util.List;

@Data
public class InvestmentTransactionPageResponse {
    private List<InvestmentTransactionResponse> items;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;
}
