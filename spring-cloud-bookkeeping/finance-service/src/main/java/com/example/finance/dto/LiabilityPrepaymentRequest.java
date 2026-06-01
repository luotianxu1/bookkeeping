package com.example.finance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LiabilityPrepaymentRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    private LocalDateTime paidAt;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}
