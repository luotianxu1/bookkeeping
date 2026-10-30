package com.example.tool.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PhotographyOrderOverviewTrendPointResponse {

    private String key;
    private String label;
    private Integer orderCount;
    private Integer shotCount;
    private Integer pendingCount;
    private BigDecimal totalIncome;
    private BigDecimal contractAmount;
}
