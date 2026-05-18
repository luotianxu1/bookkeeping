package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class GoldLiquidationResponse {
    private BigDecimal cumulativeWeight;
    private BigDecimal cumulativeProfit;
    private List<GoldLiquidationRecordResponse> records;
}
