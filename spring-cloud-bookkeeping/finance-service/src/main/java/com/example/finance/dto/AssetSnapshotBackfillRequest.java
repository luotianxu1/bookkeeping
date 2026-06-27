package com.example.finance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AssetSnapshotBackfillRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    private LocalDate snapshotDate;
}
