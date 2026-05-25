package com.example.tool.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PhotographyOrderCollectFinalRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    private Long finalAccountId;

    private LocalDateTime occurredAt;
}
