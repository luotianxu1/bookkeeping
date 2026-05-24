package com.example.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InvestmentTransactionRequest {
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    @NotNull(message = "投资账户ID不能为空")
    private Long accountId;
    private Long positionId;
    @NotNull(message = "产品ID不能为空")
    private Long productId;
    @NotBlank(message = "交易类型不能为空")
    private String tradeType;
    @DecimalMin(value = "0.000000", message = "交易数量不能小于0")
    private BigDecimal quantity;
    @DecimalMin(value = "0.000000", message = "成交价格不能小于0")
    private BigDecimal price;
    @NotNull(message = "成交金额不能为空")
    @DecimalMin(value = "0.01", message = "成交金额必须大于0")
    private BigDecimal amount;
    @DecimalMin(value = "0.00", message = "手续费不能小于0")
    private BigDecimal feeAmount;
    @DecimalMin(value = "0.00", message = "税费不能小于0")
    private BigDecimal taxAmount;
    @Size(min = 3, max = 3, message = "币种编码必须为3个字符")
    private String currencyCode;
    @NotNull(message = "交易时间不能为空")
    private LocalDateTime tradeAt;
    private Long fundingAccountId;
    private String subscriptionTimeSlot;
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
