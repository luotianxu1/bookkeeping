package com.example.tool.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PhotographyOrderOverviewTypeStatResponse {

    private String type;
    private String label;
    private Integer orderCount;
    private BigDecimal totalIncome;
    private BigDecimal contractAmount;
}
