package com.example.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvestmentPositionRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "投资账户ID不能为空")
    private Long accountId;

    private Long fundingAccountId;

    private Long productId;
    private InvestmentProductRequest product;

    @DecimalMin(value = "0.000000", message = "持仓数量不能小于0")
    private BigDecimal holdingQuantity;

    @DecimalMin(value = "0.000000", message = "可用数量不能小于0")
    private BigDecimal availableQuantity;

    @DecimalMin(value = "0.000000", message = "冻结数量不能小于0")
    private BigDecimal frozenQuantity;

    @NotNull(message = "成本金额不能为空")
    @DecimalMin(value = "0.00", message = "成本金额不能小于0")
    private BigDecimal costAmount;

    @DecimalMin(value = "0.000000", message = "当前价格不能小于0")
    private BigDecimal currentPrice;

    private String subscriptionTimeSlot;

    private Boolean includeInNetWorth;
    private String status;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
