package com.example.app.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AssetTrendContributorResponse {
    private Long accountId;
    private String accountName;
    private String accountTypeCode;
    private String accountTypeLabel;
    private BigDecimal contributionAmount;
    private BigDecimal contributionRate;
}
