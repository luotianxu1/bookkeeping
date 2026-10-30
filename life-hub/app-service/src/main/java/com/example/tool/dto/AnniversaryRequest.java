package com.example.tool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AnniversaryRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "纪念日名称不能为空")
    @Size(max = 120, message = "纪念日名称不能超过120个字符")
    private String title;

    @NotNull(message = "纪念日期不能为空")
    private LocalDate anniversaryDate;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    private Integer sortOrder;
}
