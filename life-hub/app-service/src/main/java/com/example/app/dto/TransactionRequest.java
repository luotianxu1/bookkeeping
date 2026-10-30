package com.example.app.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "流水类型不能为空")
    private String type;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal amount;

    @Size(min = 3, max = 3, message = "币种编码必须为3个字符")
    private String currencyCode;

    // 收支流水必填；转账流水为空，改用 fromAccountId/toAccountId
    private Long accountId;

    // 收支流水必填；转账流水为空
    private Long categoryId;

    // 转出账户ID；转账流水必填
    private Long fromAccountId;

    // 转入账户ID；转账流水必填
    private Long toAccountId;

    @Size(max = 120, message = "标题不能超过120个字符")
    private String title;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    @NotNull(message = "发生时间不能为空")
    private LocalDateTime occurredAt;
}
