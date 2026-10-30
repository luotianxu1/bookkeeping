package com.example.tool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FoodCategoryRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "分类类型不能为空")
    private String categoryType;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 80, message = "分类名称不能超过80个字符")
    private String name;

    @NotBlank(message = "分类图标不能为空")
    @Size(max = 12, message = "分类图标不能超过12个字符")
    private String iconText;

    @NotBlank(message = "分类色系不能为空")
    @Size(max = 32, message = "分类色系不能超过32个字符")
    private String iconTone;

    @Size(max = 255, message = "分类说明不能超过255个字符")
    private String description;

    private Integer sortOrder;
    private String status;
}
