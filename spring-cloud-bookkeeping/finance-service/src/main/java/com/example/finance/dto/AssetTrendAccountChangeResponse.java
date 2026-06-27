package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AssetTrendAccountChangeResponse {
    private Long accountId;
    private String accountName;
    private String accountTypeCode;
    private String accountTypeLabel;
    private LocalDate snapshotDate;
    private BigDecimal currentAssets;
    private BigDecimal previousAssets;
    private BigDecimal changeAmount;
    private BigDecimal changeRate;
}
