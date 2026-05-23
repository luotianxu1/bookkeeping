package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class HumanRelationSummaryResponse {

    private BigDecimal netAmount;
    private BigDecimal outgoingTotal;
    private BigDecimal incomingTotal;
    private Integer accountCount;
    private Integer recordCount;
}
