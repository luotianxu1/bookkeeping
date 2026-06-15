package com.example.tool.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TravelPlanCompanionResponse {

    private Long id;
    private Long travelPlanId;
    private Long contactId;
    private String contactName;
    private String contactPhone;
    private String contactRemark;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
