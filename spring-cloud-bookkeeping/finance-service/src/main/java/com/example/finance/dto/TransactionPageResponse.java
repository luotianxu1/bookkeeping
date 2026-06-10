package com.example.finance.dto;

import lombok.Data;

import java.util.List;

@Data
public class TransactionPageResponse {
    private List<TransactionResponse> items;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;
}
