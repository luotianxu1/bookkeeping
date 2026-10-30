package com.example.app.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DebtAccountSummaryResponse {

    private BigDecimal netAmount;
    private BigDecimal payableTotal;
    private BigDecimal receivableTotal;
    private Integer accountCount;
    private Integer recordCount;
}
