package com.example.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LiabilityRecordRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "负债账户ID不能为空")
    private Long accountId;

    @DecimalMin(value = "0.01", message = "本期待还金额必须大于0")
    private BigDecimal amount;

    @Min(value = 2, message = "贷款总期数至少为2")
    private Integer installmentTotalPeriods;

    @Min(value = 1, message = "当前还款期数至少为1")
    private Integer installmentCurrentPeriod;

    @Size(min = 3, max = 3, message = "币种编码必须为3个字符")
    private String currencyCode;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;

    private LocalDateTime occurredAt;
}
