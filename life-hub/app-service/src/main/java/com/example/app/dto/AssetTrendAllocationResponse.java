package com.example.app.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AssetTrendAllocationResponse {
    private String accountTypeCode;
    private String label;
    private BigDecimal balance;
    private BigDecimal percent;
}
