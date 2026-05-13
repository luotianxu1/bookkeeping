package com.example.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryRequest {

    private Long userId;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 64, message = "分类名称不能超过64个字符")
    private String name;

    @NotBlank(message = "分类类型不能为空")
    private String type;

    @NotBlank(message = "分类图标不能为空")
    @Size(max = 64, message = "分类图标不能超过64个字符")
    private String icon;

    @Size(max = 32, message = "分类颜色不能超过32个字符")
    private String color;

    private Boolean system;
    private Integer sortOrder;
    private String status;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}
