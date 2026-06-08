package com.example.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvestmentFixedExpenseRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "固定支出名称不能为空")
    @Size(max = 120, message = "固定支出名称不能超过120个字符")
    private String name;

    @NotNull(message = "固定支出金额不能为空")
    @DecimalMin(value = "0.01", message = "固定支出金额必须大于0")
    private BigDecimal amount;

    @Size(max = 3, message = "币种编码不能超过3个字符")
    private String currencyCode;

    private Integer sortOrder;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}
