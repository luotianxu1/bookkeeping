package com.example.tool.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TravelPlanOverviewResponse {

    private Integer companionCount;
    private Integer travelerCount;
    private Integer dayCount;
    private Integer itineraryCount;
    private Integer expenseCount;
    private BigDecimal totalExpenseAmount;
    private BigDecimal perPersonExpenseAmount;
}
