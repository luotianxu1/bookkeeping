package com.example.tool.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class TravelPlanItineraryResponse {

    private Long id;
    private Long travelPlanDayId;
    private String type;
    private String title;
    private String poiName;
    private String poiId;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private LocalTime startTime;
    private String transportMode;
    private Integer distanceMeters;
    private Integer durationSeconds;
    private String remark;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
