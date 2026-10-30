package com.example.tool.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TravelPlanDetailResponse {

    private Long id;
    private Long userId;
    private String name;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private String remark;
    private String status;
    private Integer sortOrder;
    private TravelPlanOverviewResponse overview;
    private List<TravelPlanCompanionResponse> companions = new ArrayList<>();
    private List<TravelPlanDayResponse> days = new ArrayList<>();
    private List<TravelPlanExpenseResponse> expenses = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
