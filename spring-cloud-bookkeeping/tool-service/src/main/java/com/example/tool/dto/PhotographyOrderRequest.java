package com.example.tool.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PhotographyOrderRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Size(max = 120, message = "联系方式不能超过120个字符")
    private String contactInfo;

    @NotBlank(message = "订单类型不能为空")
    private String orderType;

    @NotNull(message = "拍摄时间不能为空")
    private LocalDateTime shootAt;

    @NotNull(message = "总金额不能为空")
    @DecimalMin(value = "0.00", message = "总金额不能小于0")
    private BigDecimal totalAmount;

    @NotNull(message = "订金不能为空")
    @DecimalMin(value = "0.00", message = "订金不能小于0")
    private BigDecimal depositAmount;

    @NotNull(message = "尾款不能为空")
    @DecimalMin(value = "0.00", message = "尾款不能小于0")
    private BigDecimal finalAmount;

    private Long depositAccountId;

    @Size(max = 255, message = "地址不能超过255个字符")
    private String address;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    private Integer sortOrder;
}
