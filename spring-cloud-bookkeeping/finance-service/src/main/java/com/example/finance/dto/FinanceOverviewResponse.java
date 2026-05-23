package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceOverviewResponse {

    private BigDecimal totalAssets;
}
