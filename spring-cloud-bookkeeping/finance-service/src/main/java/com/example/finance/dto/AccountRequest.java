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
public class AccountRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "账户类型ID不能为空")
    private Long accountTypeId;

    private Long contactId;

    @NotBlank(message = "账户名称不能为空")
    @Size(max = 80, message = "账户名称不能超过80个字符")
    private String name;

    @Size(max = 32, message = "账户图标不能超过32个字符")
    private String icon;

    @Size(max = 32, message = "账户颜色不能超过32个字符")
    private String color;

    @Size(min = 3, max = 3, message = "币种编码必须为3个字符")
    private String currencyCode;

    @DecimalMin(value = "0.00", message = "当前余额不能小于0")
    private BigDecimal currentBalance;

    @DecimalMin(value = "0.01", message = "贷款总额必须大于0")
    private BigDecimal loanTotalAmount;

    @DecimalMin(value = "0.00", message = "贷款利率不能小于0")
    private BigDecimal loanInterestRate;

    @Min(value = 2, message = "贷款总期数至少为2")
    private Integer loanTotalPeriods;

    @Min(value = 1, message = "每月还款日必须在1到31之间")
    @Max(value = 31, message = "每月还款日必须在1到31之间")
    private Integer loanRepaymentDay;

    private LocalDate loanStartDate;

    @NotNull(message = "是否计入总资产不能为空")
    private Boolean includeInNetWorth;

    private Integer sortOrder;
    private String status;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}
