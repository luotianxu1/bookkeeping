package com.example.tool.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TravelPlanExpenseRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "费用类型不能为空")
    private String type;

    @NotBlank(message = "费用名称不能为空")
    @Size(max = 120, message = "费用名称不能超过120个字符")
    private String title;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal amount;

    private Long payerContactId;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    private Integer sortOrder;
}
