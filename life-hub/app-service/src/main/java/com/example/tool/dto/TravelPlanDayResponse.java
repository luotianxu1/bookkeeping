package com.example.tool.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TravelPlanDayResponse {

    private Long id;
    private Long travelPlanId;
    private Integer dayIndex;
    private String title;
    private LocalDate travelDate;
    private Integer sortOrder;
    private List<TravelPlanItineraryResponse> itineraries = new ArrayList<>();
    private List<TravelPlanExpenseResponse> expenses = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
