package com.example.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AccountTypeRequest {

    @NotBlank(message = "账户类型编码不能为空")
    @Size(max = 64, message = "账户类型编码不能超过64个字符")
    private String code;

    @NotBlank(message = "账户类型名称不能为空")
    @Size(max = 64, message = "账户类型名称不能超过64个字符")
    private String name;

    @NotBlank(message = "账户大类不能为空")
    private String category;

    @NotBlank(message = "余额方向不能为空")
    private String balanceDirection;

    @NotNull(message = "是否默认计入总资产不能为空")
    private Boolean includeInNetWorthDefault;

    @NotNull(message = "是否允许透支不能为空")
    private Boolean allowOverdraft;

    private Boolean system;
    private Integer sortOrder;
    private String status;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}
