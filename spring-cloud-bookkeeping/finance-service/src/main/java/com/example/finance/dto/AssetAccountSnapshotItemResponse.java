package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AssetAccountSnapshotItemResponse {

    private Long userId;

    private Long accountId;

    private String accountName;

    private String accountTypeCode;

    private String accountTypeLabel;

    private BigDecimal totalAssets;

    private BigDecimal currentAssets;

    private BigDecimal changeAmount;
}
