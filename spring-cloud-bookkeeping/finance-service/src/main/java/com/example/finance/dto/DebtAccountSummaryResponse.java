package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DebtAccountSummaryResponse {

    private BigDecimal totalAmount;
    private Integer accountCount;
}
