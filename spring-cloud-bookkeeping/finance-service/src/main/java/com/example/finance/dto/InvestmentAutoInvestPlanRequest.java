package com.example.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InvestmentAutoInvestPlanRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "投资账户ID不能为空")
    private Long accountId;

    @NotNull(message = "投资持仓ID不能为空")
    private Long positionId;

    @NotNull(message = "资金账户不能为空")
    private Long fundingAccountId;

    @NotBlank(message = "定投周期不能为空")
    private String frequency;

    @NotNull(message = "定投金额不能为空")
    @DecimalMin(value = "0.01", message = "定投金额必须大于0")
    private BigDecimal amount;

    @Size(min = 3, max = 3, message = "币种编码必须为3个字符")
    private String currencyCode;

    @NotNull(message = "下次执行日期不能为空")
    private LocalDate nextExecuteDate;

    private String status;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
