package com.example.tool.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PhotographyOrderOverviewBucketResponse {

    private String key;
    private String label;
    private String subLabel;
    private Integer orderCount;
    private Integer shotCount;
    private Integer pendingCount;
    private BigDecimal totalIncome;
    private BigDecimal contractAmount;
    private Boolean selected;
    private Boolean currentScope;
}
