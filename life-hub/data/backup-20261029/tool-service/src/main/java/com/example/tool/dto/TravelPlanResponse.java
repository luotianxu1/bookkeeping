package com.example.tool.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TravelPlanResponse {

    private Long id;
    private Long userId;
    private String name;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private String remark;
    private String status;
    private Integer sortOrder;
    private Integer companionCount;
    private Integer travelerCount;
    private Integer dayCount;
    private Integer expenseCount;
    private BigDecimal totalExpenseAmount;
    private BigDecimal perPersonExpenseAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
