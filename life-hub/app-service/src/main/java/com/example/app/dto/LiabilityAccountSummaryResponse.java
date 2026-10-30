package com.example.app.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LiabilityAccountSummaryResponse {

    private BigDecimal totalAmount;
    private Integer accountCount;
    private Integer recordCount;
}
