package com.example.app.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionAnalysisTrendPointResponse {

    private String key;
    private String label;
    private BigDecimal income;
    private BigDecimal expense;
    private BigDecimal surplus;
}
