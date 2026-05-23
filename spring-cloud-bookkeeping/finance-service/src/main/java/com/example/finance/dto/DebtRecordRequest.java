package com.example.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DebtRecordRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "债务账户ID不能为空")
    private Long accountId;

    private Long fundingAccountId;

    @NotBlank(message = "债务方向不能为空")
    private String direction;

    @NotNull(message = "债务金额不能为空")
    @DecimalMin(value = "0.01", message = "债务金额必须大于0")
    private BigDecimal amount;

    @Size(min = 3, max = 3, message = "币种编码必须为3个字符")
    private String currencyCode;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;

    private LocalDateTime occurredAt;
}
