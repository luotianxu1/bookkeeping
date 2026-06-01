package com.example.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RenewalSubscriptionRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "续费名称不能为空")
    @Size(max = 80, message = "续费名称不能超过80个字符")
    private String name;

    @Size(max = 80, message = "服务方名称不能超过80个字符")
    private String providerName;

    @NotNull(message = "续费金额不能为空")
    @DecimalMin(value = "0.01", message = "续费金额必须大于0")
    private BigDecimal amount;

    @Size(max = 3, message = "币种编码不能超过3个字符")
    private String currencyCode;

    @NotNull(message = "请选择扣费账户")
    private Long fundingAccountId;

    @NotNull(message = "请填写每月扣费日")
    @Min(value = 1, message = "每月扣费日必须在1到31之间")
    @Max(value = 31, message = "每月扣费日必须在1到31之间")
    private Integer billingDay;

    @Size(max = 16, message = "扣费周期长度不能超过16个字符")
    private String billingCycle;

    private LocalDate nextBillingDate;

    @Size(max = 16, message = "状态长度不能超过16个字符")
    private String status;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
