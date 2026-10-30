package com.example.tool.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TravelPlanCompanionRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "联系人不能为空")
    private Long contactId;

    private Integer sortOrder;
}
