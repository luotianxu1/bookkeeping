package com.example.tool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "联系人姓名不能为空")
    @Size(max = 80, message = "联系人姓名不能超过80个字符")
    private String name;

    @Size(max = 32, message = "手机号不能超过32个字符")
    private String phone;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    private Integer sortOrder;
    private String status;
}
