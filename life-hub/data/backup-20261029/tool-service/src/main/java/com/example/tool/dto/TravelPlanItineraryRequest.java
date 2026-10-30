package com.example.tool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
public class TravelPlanItineraryRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "行程类型不能为空")
    private String type;

    @NotBlank(message = "行程标题不能为空")
    @Size(max = 120, message = "行程标题不能超过120个字符")
    private String title;

    @Size(max = 120, message = "地点名称不能超过120个字符")
    private String poiName;

    @Size(max = 64, message = "地点ID不能超过64个字符")
    private String poiId;

    @Size(max = 255, message = "地址不能超过255个字符")
    private String address;

    private BigDecimal longitude;
    private BigDecimal latitude;
    private LocalTime startTime;
    private String transportMode;
    private Integer distanceMeters;
    private Integer durationSeconds;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    private Integer sortOrder;
}
