package com.example.app.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RenewalSubscriptionSummaryResponse {

    private Integer activeCount;
    private Integer pausedCount;
    private Integer dueThisMonthCount;
    private BigDecimal monthlyAmount;
    private BigDecimal dueThisMonthAmount;
}
