package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class AssetAccountSnapshotResponse {

    private Long userId;

    private LocalDate snapshotDate;

    private BigDecimal totalAssets;

    private BigDecimal currentTotalAssets;

    private BigDecimal changeAmount;

    private List<AssetAccountSnapshotItemResponse> accounts;
}
