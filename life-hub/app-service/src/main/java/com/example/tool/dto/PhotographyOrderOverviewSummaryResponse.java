package com.example.tool.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PhotographyOrderOverviewSummaryResponse {

    private Integer totalOrders;
    private Integer shotOrders;
    private Integer pendingOrders;
    private BigDecimal totalContractAmount;
    private BigDecimal totalReceivedAmount;
    private BigDecimal totalDepositAmount;
    private BigDecimal totalFinalAmount;
    private BigDecimal depositIncome;
    private BigDecimal finalIncome;
    private BigDecimal pendingFinalAmount;
    private BigDecimal averageContractAmount;
}
