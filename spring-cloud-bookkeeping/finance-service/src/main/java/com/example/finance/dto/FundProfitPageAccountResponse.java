package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FundProfitPageAccountResponse {
    private Long accountId;
    private String accountName;
    private BigDecimal holdingAmount;
    private BigDecimal totalProfit;
    private BigDecimal totalProfitRate;
    private Integer fundCount;
}
