package com.example.finance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LiabilityRepaymentRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    private LocalDateTime paidAt;
}
