package com.example.tool.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FoodIngredientRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "食材分类不能为空")
    private Long categoryId;

    @NotBlank(message = "食材名称不能为空")
    @Size(max = 80, message = "食材名称不能超过80个字符")
    private String name;

    @DecimalMin(value = "0.0", inclusive = true, message = "库存数量不能小于0")
    private BigDecimal stockAmount;

    @Size(max = 16, message = "单位不能超过16个字符")
    private String unit;

    @DecimalMin(value = "0.0", inclusive = true, message = "补货线不能小于0")
    private BigDecimal reorderLevel;

    @Size(max = 80, message = "存放位置不能超过80个字符")
    private String storageLocation;

    private String status;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String note;

    private Integer sortOrder;
}
