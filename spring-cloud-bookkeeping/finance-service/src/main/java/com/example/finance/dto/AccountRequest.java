package com.example.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "账户类型ID不能为空")
    private Long accountTypeId;

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

    @NotNull(message = "是否计入总资产不能为空")
    private Boolean includeInNetWorth;

    private Integer sortOrder;
    private String status;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}
